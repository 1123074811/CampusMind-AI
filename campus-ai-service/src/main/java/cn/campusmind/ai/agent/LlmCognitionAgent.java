package cn.campusmind.ai.agent;

import cn.campusmind.ai.agent.rules.CognitionRules;
import cn.campusmind.ai.config.AiModeProperties;
import cn.campusmind.ai.domain.CampusEventCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring AI {@link ChatModel} 的认知 Agent。
 *
 * <p>当 {@code campus.ai.mode=llm} 时启用。用大模型对非结构化文本做结构化事件抽取，
 * 通过 {@link BeanOutputConverter} 约束输出为 {@link CampusEventCandidate}。
 * 模型调用异常或输出无法解析时，降级到 {@link CognitionRules} 规则抽取，
 * 保证导入流程不被 LLM 故障阻断。
 *
 * <p>符合设计文档要求：Agent 输出必须结构化，并记录 prompt 版本与模型版本。
 */
@Service
@ConditionalOnProperty(name = "campus.ai.mode", havingValue = "llm")
public class LlmCognitionAgent implements CognitionAgent {

    private static final Logger log = LoggerFactory.getLogger(LlmCognitionAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是校园事件抽取助手。从用户提供的非结构化文本中抽取校园事件的结构化信息。
            字段含义：title 标题、eventType 事件类型(EXAM/HOMEWORK/COURSE/LECTURE/COMPETITION/ACTIVITY/SERVICE/NOTICE/OTHER)、
            summary 摘要、startTime/endTime ISO8601 时间、location 地点、organizer 主办方、
            targetScopes 面向范围、tags 标签、confidence 置信度(0-1)、needHumanReview 是否需人工复核、reason 判断依据。
            字段缺失返回 null，列表缺失返回空数组。只返回 JSON，不要任何解释文字。
            """;

    private final ChatModel chatModel;
    private final AiModeProperties properties;

    public LlmCognitionAgent(ChatModel chatModel, AiModeProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public CampusEventCandidate extract(String sourceType, String plainText) {
        BeanOutputConverter<CampusEventCandidate> converter = new BeanOutputConverter<>(CampusEventCandidate.class);
        String userContent = "来源类型：" + (sourceType == null ? "USER_TEXT" : sourceType) + "\n待抽取文本：\n" + plainText;
        Prompt prompt = new Prompt(
                new SystemMessage(SYSTEM_PROMPT + "\n" + converter.getFormat()),
                new UserMessage(userContent)
        );
        try {
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
            CampusEventCandidate candidate = converter.convert(content);
            if (candidate != null) {
                log.debug("LLM 认知抽取成功 model={} prompt={} sourceType={}",
                        properties.modelVersion(), properties.promptVersion(), sourceType);
                return candidate;
            }
            log.warn("LLM 输出无法解析为 CampusEventCandidate，降级规则抽取。raw={}", content);
        } catch (RuntimeException ex) {
            log.warn("LLM 认知抽取异常，降级规则抽取。sourceType={}", sourceType, ex);
        }
        return CognitionRules.extract(sourceType, plainText);
    }
}
