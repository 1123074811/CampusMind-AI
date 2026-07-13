package cn.campusmind.crawler.application;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsPolicyTest {

    private static final String ROBOTS = """
            User-agent: *
            Disallow: /private/

            User-agent: CampusEventBot
            Disallow: /admin/
            Allow: /admin/public/
            Disallow: /download/*.zip$
            """;

    @Test
    void appliesMostSpecificAgentAndLongestMatchingRule() {
        assertFalse(RobotsPolicy.isAllowed(ROBOTS, "CampusEventBot", URI.create("https://example.edu/admin/users")));
        assertTrue(RobotsPolicy.isAllowed(ROBOTS, "CampusEventBot", URI.create("https://example.edu/admin/public/news")));
        assertFalse(RobotsPolicy.isAllowed(ROBOTS, "CampusEventBot", URI.create("https://example.edu/download/a.zip")));
        assertTrue(RobotsPolicy.isAllowed(ROBOTS, "CampusEventBot", URI.create("https://example.edu/private/page")));
    }
}
