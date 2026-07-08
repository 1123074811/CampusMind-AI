package cn.campusmind.crawler.application;

import cn.campusmind.crawler.config.CrawlerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PublicWebFetcher {

    private final RestClient restClient;
    private final CrawlerProperties crawlerProperties;

    public PublicWebFetcher(RestClient restClient, CrawlerProperties crawlerProperties) {
        this.restClient = restClient;
        this.crawlerProperties = crawlerProperties;
    }

    public FetchResult fetch(String url) {
        return restClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, crawlerProperties.getUserAgent())
                .exchange((request, response) -> new FetchResult(
                        response.getStatusCode().value(),
                        response.getHeaders().getETag(),
                        response.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED),
                        readBody(response.getBody())
                ));
    }

    private String readBody(java.io.InputStream body) throws IOException {
        return body == null ? "" : StreamUtils.copyToString(body, StandardCharsets.UTF_8);
    }

    public record FetchResult(
            int httpStatus,
            String etag,
            String lastModified,
            String body
    ) {
    }
}
