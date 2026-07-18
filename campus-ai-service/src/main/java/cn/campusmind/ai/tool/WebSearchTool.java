package cn.campusmind.ai.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool {

    private final RestClient client;
    private final String apiKey;
    private final boolean enabled;
    private final int maxResults;

    @Autowired
    public WebSearchTool(RestClient.Builder builder,
                         @Value("${campus.ai.web-search.base-url:https://api.tavily.com}") String baseUrl,
                         @Value("${campus.ai.web-search.api-key:}") String apiKey,
                         @Value("${campus.ai.web-search.enabled:true}") boolean enabled,
                         @Value("${campus.ai.web-search.max-results:5}") int maxResults) {
        this(buildClient(builder, baseUrl), apiKey, enabled, maxResults);
    }

    WebSearchTool(RestClient client, String apiKey, boolean enabled, int maxResults) {
        this.client = client;
        this.apiKey = apiKey;
        this.enabled = enabled && StringUtils.hasText(apiKey);
        this.maxResults = Math.min(Math.max(maxResults, 1), 10);
    }

    private static RestClient buildClient(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public boolean enabled() {
        return enabled;
    }

    @Tool(name = "search_web", description = "搜索互联网中的最新或项目知识库未收录的信息，返回标题、网页链接和相关内容摘要。适用于招生分数线、政策、排名、新闻等需要外部证据的问题。")
    public WebSearchResponse searchWeb(
            @ToolParam(description = "完整、具体的中文搜索问题，最长 500 字") String query) {
        if (!enabled) {
            throw new IllegalStateException("联网搜索未配置");
        }
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("搜索问题不能为空且不能超过 500 字");
        }
        TavilyResponse response = client.post()
                .uri("/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(Map.of(
                        "query", normalized,
                        "search_depth", "basic",
                        "topic", "general",
                        "country", "china",
                        "include_answer", false,
                        "include_raw_content", false,
                        "max_results", maxResults))
                .retrieve()
                .body(TavilyResponse.class);
        List<WebSearchResult> results = response == null || response.results() == null
                ? List.of()
                : response.results().stream()
                .filter(item -> StringUtils.hasText(item.title()) && isHttpUrl(item.url()))
                .limit(maxResults)
                .map(item -> new WebSearchResult(
                        item.title().trim(), item.url().trim(), safeText(item.content()), item.score()))
                .toList();
        return new WebSearchResponse(normalized, results);
    }

    private static boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            String scheme = URI.create(value.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= 1200 ? text : text.substring(0, 1200);
    }

    public record WebSearchResponse(String query, List<WebSearchResult> results) {
        public WebSearchResponse {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record WebSearchResult(String title, String url, String content, double score) {
    }

    private record TavilyResponse(List<TavilyResult> results) {
    }

    private record TavilyResult(String title, String url, String content,
                                @JsonProperty("score") double score) {
    }
}
