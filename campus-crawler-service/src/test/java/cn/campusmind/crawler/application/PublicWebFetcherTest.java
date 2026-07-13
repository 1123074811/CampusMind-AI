package cn.campusmind.crawler.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicWebFetcherTest {

    @Test
    void rejectsNonPublicAndCredentialedUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> PublicWebFetcher.validatePublicUrl("http://127.0.0.1/admin"));
        assertThrows(IllegalArgumentException.class,
                () -> PublicWebFetcher.validatePublicUrl("http://[::1]/admin"));
        assertThrows(IllegalArgumentException.class,
                () -> PublicWebFetcher.validatePublicUrl("https://user:secret@8.8.8.8/path"));
        assertThrows(IllegalArgumentException.class,
                () -> PublicWebFetcher.validatePublicUrl("file:///etc/passwd"));
    }

    @Test
    void acceptsPublicHttpAddress() {
        assertEquals("8.8.8.8", PublicWebFetcher.validatePublicUrl("https://8.8.8.8/path").getHost());
    }
}
