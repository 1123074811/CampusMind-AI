package cn.campusmind.crawler.application;

import cn.campusmind.crawler.config.CrawlerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PublicWebFetcher {

    private final RestClient restClient;
    private final CrawlerProperties crawlerProperties;

    public PublicWebFetcher(RestClient restClient, CrawlerProperties crawlerProperties) {
        this.restClient = restClient;
        this.crawlerProperties = crawlerProperties;
    }

    public FetchResult fetch(String url) {
        ResponseEntity<String> response = restClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, crawlerProperties.getUserAgent())
                .retrieve()
                .toEntity(String.class);
        HttpStatusCode statusCode = response.getStatusCode();
        return new FetchResult(statusCode.value(), response.getHeaders().getETag(),
                response.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED), response.getBody());
    }

    public record FetchResult(
            int httpStatus,
            String etag,
            String lastModified,
            String body
    ) {
    }
}
