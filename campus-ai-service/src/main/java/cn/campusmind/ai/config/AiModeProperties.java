package cn.campusmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务运行模式配置。
 *
 * <ul>
 *   <li>{@code rule}（默认）：使用规则版 Agent + 内存向量库，不依赖外部 LLM 与向量数据库，
 *       便于本地开发与测试</li>
 *   <li>{@code llm}：使用基于 Spring AI {@code ChatModel} 的 LLM 版 Agent + PGVector 向量库，
 *       需配置 OpenAI 兼容的 chat/embedding 模型与 PG 数据源</li>
 * </ul>
 *
 * <p>对应配置前缀 {@code campus.ai}。
 */
@ConfigurationProperties(prefix = "campus.ai")
public record AiModeProperties(
        Mode mode,
        String modelVersion,
        String promptVersion
) {

    public enum Mode {
        RULE,
        LLM
    }

    public Mode mode() {
        return mode == null ? Mode.RULE : mode;
    }

    public String modelVersion() {
        return modelVersion == null || modelVersion.isBlank() ? "unknown" : modelVersion;
    }

    public String promptVersion() {
        return promptVersion == null || promptVersion.isBlank() ? "unknown" : promptVersion;
    }
}
