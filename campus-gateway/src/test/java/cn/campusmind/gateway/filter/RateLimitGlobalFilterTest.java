package cn.campusmind.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitGlobalFilterTest {

    @Test
    void usesPeerAddressInsteadOfClientSuppliedForwardedHeader() {
        var request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "198.51.100.1")
                .remoteAddress(new InetSocketAddress("203.0.113.1", 443))
                .build();

        assertEquals("203.0.113.1", RateLimitGlobalFilter.extractClientIp(request));
    }
}
