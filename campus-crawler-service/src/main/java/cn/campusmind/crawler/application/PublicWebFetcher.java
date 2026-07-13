package cn.campusmind.crawler.application;

import cn.campusmind.crawler.config.CrawlerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

@Component
public class PublicWebFetcher {

    private static final int MAX_REDIRECTS = 5;

    private final RestClient restClient;
    private final CrawlerProperties crawlerProperties;

    public PublicWebFetcher(RestClient restClient, CrawlerProperties crawlerProperties) {
        this.restClient = restClient;
        this.crawlerProperties = crawlerProperties;
    }

    public FetchResult fetch(String url) {
        return fetch(url, null, null);
    }

    public FetchResult fetch(String url, String etag, String lastModified) {
        URI current = validatePublicUrl(url);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            URI requestUri = current;
            RawResponse response = restClient.get()
                    .uri(requestUri)
                    .header(HttpHeaders.USER_AGENT, crawlerProperties.getUserAgent())
                    .headers(headers -> {
                        if (etag != null && !etag.isBlank()) headers.setIfNoneMatch(etag);
                        if (lastModified != null && !lastModified.isBlank()) headers.set(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
                    })
                    .exchange((request, raw) -> new RawResponse(
                            raw.getStatusCode().value(),
                            raw.getHeaders().getETag(),
                            raw.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED),
                            raw.getHeaders().getLocation(),
                            readBody(raw.getBody())
                    ));
            if (response.status() < 300 || response.status() >= 400) {
                return new FetchResult(response.status(), response.etag(), response.lastModified(), response.body());
            }
            if (response.location() == null) {
                throw new IllegalStateException("网页重定向缺少 Location");
            }
            current = validatePublicUrl(requestUri.resolve(response.location()).toString());
        }
        throw new IllegalStateException("网页重定向次数超过限制");
    }

    private String readBody(java.io.InputStream body) throws IOException {
        if (body == null) return "";
        byte[] bytes = body.readNBytes(crawlerProperties.getMaxResponseBytes() + 1);
        if (bytes.length > crawlerProperties.getMaxResponseBytes()) {
            throw new IllegalStateException("网页响应体超过大小限制");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static URI validatePublicUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("网页地址格式无效", ex);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("网页地址仅支持有效的 HTTP(S) URL");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("网页地址不得包含用户名或密码");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (!isPublic(address)) {
                    throw new IllegalArgumentException("网页地址解析到非公网 IP");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("网页域名无法解析", ex);
        }
        return uri;
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) != 0xfc;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first != 0 && first != 127 && !(first == 100 && second >= 64 && second <= 127);
    }

    public record FetchResult(
            int httpStatus,
            String etag,
            String lastModified,
            String body
    ) {
    }

    private record RawResponse(int status, String etag, String lastModified, URI location, String body) {
    }
}
