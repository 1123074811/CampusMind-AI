package cn.campusmind.ai.config;

import cn.campusmind.ai.agent.CognitionAgent;
import cn.campusmind.ai.agent.DecisionAgent;
import cn.campusmind.ai.agent.LlmCognitionAgent;
import cn.campusmind.ai.agent.LlmDecisionAgent;
import cn.campusmind.ai.agent.rules.CognitionRules;
import cn.campusmind.ai.agent.rules.DecisionRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运行时可变 AI 配置。支持管理员在页面修改模式/Base URL/模型/API Key 后立即生效，
 * 无需重启 AI 服务。
 */
@Component
public class RuntimeAiConfig {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAiConfig.class);

    private final AiModeProperties properties;

    private volatile AiModeProperties.Mode currentMode;
    private volatile ChatModel dynamicChatModel;
    private volatile CognitionAgent dynamicCognitionAgent;
    private volatile DecisionAgent dynamicDecisionAgent;

    public RuntimeAiConfig(AiModeProperties properties) {
        this.properties = properties;
        this.currentMode = properties.mode();
    }

    public AiModeProperties.Mode currentMode() {
        return currentMode;
    }

    /**
     * 根据当前运行时模式选择认知 Agent。LLM 模式返回动态构建的 Agent，否则返回规则 Agent。
     */
    public CognitionAgent resolveCognitionAgent() {
        if (currentMode == AiModeProperties.Mode.LLM && dynamicCognitionAgent != null) {
            return dynamicCognitionAgent;
        }
        return (sourceType, plainText) -> CognitionRules.extract(sourceType, plainText);
    }

    /**
     * 根据当前运行时模式选择决策 Agent。
     */
    public DecisionAgent resolveDecisionAgent() {
        if (currentMode == AiModeProperties.Mode.LLM && dynamicDecisionAgent != null) {
            return dynamicDecisionAgent;
        }
        return (query, userScopes, usePersonalProfile) ->
                DecisionRules.plan(query, userScopes, usePersonalProfile);
    }

    /**
     * 返回当前可用的 ChatModel，用于闲聊等场景。LLM 模式返回动态模型，否则返回 null。
     */
    public ChatModel resolveChatModel() {
        return dynamicChatModel;
    }

    /**
     * 热加载：更新模式并重建 ChatModel（LLM 模式下）。
     *
     * @return 当前生效的模式
     */
    public AiModeProperties.Mode reload(String mode, String baseUrl, String model, String apiKey) {
        AiModeProperties.Mode newMode = "llm".equalsIgnoreCase(mode) ? AiModeProperties.Mode.LLM : AiModeProperties.Mode.RULE;
        this.currentMode = newMode;

        if (newMode == AiModeProperties.Mode.LLM && baseUrl != null && model != null && apiKey != null && !apiKey.isBlank()) {
            try {
                OpenAiApi api = OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .build();
                this.dynamicChatModel = OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(OpenAiChatOptions.builder()
                                .model(model)
                                .temperature(0.2)
                                .build())
                        .build();
                this.dynamicCognitionAgent = new LlmCognitionAgent(dynamicChatModel, properties);
                this.dynamicDecisionAgent = new LlmDecisionAgent(dynamicChatModel, properties);
                log.info("AI 配置热加载成功: mode=llm, model={}, baseUrl={}", model, baseUrl);
            } catch (Exception ex) {
                log.error("AI 配置热加载失败，回退到规则模式", ex);
                this.currentMode = AiModeProperties.Mode.RULE;
                this.dynamicChatModel = null;
                this.dynamicCognitionAgent = null;
                this.dynamicDecisionAgent = null;
                return AiModeProperties.Mode.RULE;
            }
        } else {
            this.dynamicChatModel = null;
            this.dynamicCognitionAgent = null;
            this.dynamicDecisionAgent = null;
            log.info("AI 配置热加载成功: mode=rule");
        }
        return this.currentMode;
    }
}
