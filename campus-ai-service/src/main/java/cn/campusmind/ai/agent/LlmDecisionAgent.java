package cn.campusmind.ai.agent;

import cn.campusmind.ai.agent.rules.DecisionRules;
import cn.campusmind.ai.config.AiModeProperties;
import cn.campusmind.ai.domain.SearchPlan;
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

import java.util.List;

/**
 * 基于 Spring AI {@link ChatModel} 的决策 Agent。
 *
 * <p>当 {@code campus.ai.mode=llm} 时启用。用大模型识别用户查询意图并生成检索计划，
 * 通过 {@link BeanOutputConverter} 约束输出为 {@link SearchPlan}。
 * 模型调用异常或输出无法解析时，降级到 {@link DecisionRules} 规则识别。
 */
@Service
@ConditionalOnProperty(name = "campus.ai.mode", havingValue = "llm")
public class LlmDecisionAgent implements DecisionAgent {

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是校园事件检索的意图识别助手。根据用户查询识别意图并生成检索计划。
            intent 取值：CASUAL_CHAT(问候/闲聊/感谢/告别)、FEED_QUERY(按条件查信息流)、SEMANTIC_SEARCH(语义检索相关事件)、
            PERSONAL_SCHEDULE(个人日程/我的作业课表)、QA_EXPLAIN(解释说明类问答)、IMPORT_HELP(导入帮助)。
            eventTypes 事件类型列表(EXAM/HOMEWORK/COURSE/LECTURE/COMPETITION/ACTIVITY/SERVICE/NOTICE/OTHER)；
            timeRange 取值 TODAY/TOMORROW/THIS_WEEK/RECENT/ANY；useVectorSearch 是否需向量检索；
            usePersonalProfile 是否需个人画像；topK 召回数量。只返回 JSON，不要解释文字。
            """;

    private final ChatModel chatModel;
    private final AiModeProperties properties;

    public LlmDecisionAgent(ChatModel chatModel, AiModeProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public SearchPlan plan(String query, List<String> userScopes, boolean usePersonalProfile) {
        BeanOutputConverter<SearchPlan> converter = new BeanOutputConverter<>(SearchPlan.class);
        String userContent = "用户查询：" + query
                + "\n用户范围：" + (userScopes == null ? List.of() : userScopes)
                + "\n是否启用个人画像：" + usePersonalProfile;
        Prompt prompt = new Prompt(
                new SystemMessage(SYSTEM_PROMPT + "\n" + converter.getFormat()),
                new UserMessage(userContent)
        );
        try {
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
            SearchPlan plan = converter.convert(content);
            if (plan != null) {
                log.debug("LLM 决策识别成功 model={} prompt={} intent={}",
                        properties.modelVersion(), properties.promptVersion(), plan.intent());
                return plan;
            }
            log.warn("LLM 输出无法解析为 SearchPlan，降级规则识别。raw={}", content);
        } catch (RuntimeException ex) {
            log.warn("LLM 决策识别异常，降级规则识别。query={}", query, ex);
        }
        return DecisionRules.plan(query, userScopes, usePersonalProfile);
    }
}
