package cn.campusmind.ai.agent;

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
 * 模型调用异常或输出无法解析时抛出异常；是否降级由应用层根据调用场景决定。
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
            summary 摘要（70-160 字，2-3 句独立改写，说明事项、关键结论和明确的下一步/时间）、startTime/endTime ISO8601 时间、location 地点、organizer 主办方、
            targetScopes 面向范围、tags 标签、needHumanReview 是否需人工复核、reason 判断依据。
            keyDates 关键时间集合。requiredActions 只记录用户可提前完成、适合加入待办并能明确勾选完成的动作，
            例如报名、提交或上传材料、缴费、预约、申请、填写、打印下载、领取办理、提前准备材料，最多 3 项。
            考场纪律、禁止事项、到场安检、迟到及入场离场规则、结果查询或公告说明不得写入 requiredActions；
            没有可形成待办的动作时返回空数组。
            所有事件优先抽取实际出现的时间、地点、主办方、面向对象、关键日期、需办理事项和附件。
            仅 COMPETITION 或确有报名流程的 ACTIVITY 才抽取 registrationStartTime 报名开始时间、registrationDeadline 报名截止时间、
            eventDuration 持续时间、requiredMaterials 所需材料、registrationUrl 报名网址、
            participationMethod 参赛方式、teamRequirement 组队要求、attachments 附件清单。
            不得因“截止”“提交”“报告厅”把通知、招聘、会议或活动误判为 HOMEWORK 或 LECTURE；
            HOMEWORK 必须明确出现作业、课程作业，或雨课堂作业语境。
            originalItemId 和 originalUrl 返回 null，由系统在模型调用后写入，禁止编造。
            summary 不得截取原文开头、逐句压缩或复制原文；不得连续复用原文超过 12 个汉字。
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
            throw new IllegalStateException("LLM 输出无法解析为 CampusEventCandidate");
        } catch (RuntimeException ex) {
            log.warn("LLM 认知抽取异常。sourceType={}", sourceType, ex);
            throw ex;
        }
    }
}
