package cn.campusmind.crawler.application;

import cn.campusmind.crawler.config.CrawlerProperties;
import cn.campusmind.crawler.domain.DataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
class RobotsPolicy {

    private final PublicWebFetcher fetcher;
    private final CrawlerProperties properties;
    private final ConcurrentHashMap<String, CachedRules> cache = new ConcurrentHashMap<>();

    RobotsPolicy(PublicWebFetcher fetcher, CrawlerProperties properties) {
        this.fetcher = fetcher;
        this.properties = properties;
    }

    void assertAllowed(DataSource source, String targetUrl) {
        if (!properties.isRobotsEnforced()) return;
        URI target = PublicWebFetcher.validatePublicUrl(targetUrl);
        URI robots = robotsUri(source, target);
        if (!sameOrigin(target, robots)) {
            throw new IllegalArgumentException("robots.txt 地址必须与数据源同源");
        }
        String key = origin(robots);
        CachedRules cached = cache.get(key);
        if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
            cached = load(robots);
            cache.put(key, cached);
        }
        if (!isAllowed(cached.body(), productToken(properties.getUserAgent()), target)) {
            throw new IllegalStateException("robots.txt 禁止采集该地址");
        }
    }

    private CachedRules load(URI robots) {
        PublicWebFetcher.FetchResult result = fetcher.fetch(robots.toString());
        if (result.httpStatus() == 404) {
            return new CachedRules("", expiresAt());
        }
        if (result.httpStatus() / 100 != 2) {
            throw new IllegalStateException("robots.txt 获取失败，HTTP " + result.httpStatus());
        }
        return new CachedRules(result.body(), expiresAt());
    }

    private Instant expiresAt() {
        return Instant.now().plus(Duration.ofHours(Math.max(1, properties.getRobotsCacheTtlHours())));
    }

    private URI robotsUri(DataSource source, URI target) {
        if (StringUtils.hasText(source.getRobotsUrl())) {
            return PublicWebFetcher.validatePublicUrl(source.getRobotsUrl());
        }
        return PublicWebFetcher.validatePublicUrl(target.resolve("/robots.txt").toString());
    }

    static boolean isAllowed(String robotsText, String userAgent, URI target) {
        List<Group> groups = parse(robotsText);
        String normalizedAgent = userAgent.toLowerCase(Locale.ROOT);
        int bestAgentLength = groups.stream()
                .filter(group -> group.matches(normalizedAgent))
                .mapToInt(group -> group.specificity(normalizedAgent))
                .max().orElse(-1);
        if (bestAgentLength < 0) return true;

        String rawPath = target.getRawPath() == null || target.getRawPath().isEmpty() ? "/" : target.getRawPath();
        String path = target.getRawQuery() == null ? rawPath : rawPath + "?" + target.getRawQuery();
        Rule winner = groups.stream()
                .filter(group -> group.matches(normalizedAgent)
                        && group.specificity(normalizedAgent) == bestAgentLength)
                .flatMap(group -> group.rules().stream())
                .filter(rule -> rule.matches(path))
                .max((left, right) -> {
                    int length = Integer.compare(left.specificity(), right.specificity());
                    return length != 0 ? length : Boolean.compare(left.allow(), right.allow());
                }).orElse(null);
        return winner == null || winner.allow();
    }

    private static List<Group> parse(String text) {
        List<Group> groups = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        boolean rulesStarted = false;
        for (String rawLine : (text == null ? "" : text).split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if ("user-agent".equals(key)) {
                if (rulesStarted) {
                    groups.add(new Group(List.copyOf(agents), List.copyOf(rules)));
                    agents.clear();
                    rules.clear();
                    rulesStarted = false;
                }
                agents.add(value.toLowerCase(Locale.ROOT));
            } else if (("allow".equals(key) || "disallow".equals(key)) && !agents.isEmpty()) {
                rulesStarted = true;
                if (!value.isEmpty()) rules.add(Rule.of("allow".equals(key), value));
            }
        }
        if (!agents.isEmpty()) groups.add(new Group(List.copyOf(agents), List.copyOf(rules)));
        return groups;
    }

    private static String productToken(String userAgent) {
        String value = userAgent == null ? "*" : userAgent.trim();
        int end = value.indexOf('/');
        if (end < 0) end = value.indexOf(' ');
        return (end < 0 ? value : value.substring(0, end)).toLowerCase(Locale.ROOT);
    }

    private static String origin(URI uri) {
        int port = uri.getPort();
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (port < 0 ? "" : ":" + port);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return origin(left).equals(origin(right));
    }

    private record CachedRules(String body, Instant expiresAt) {}

    private record Group(List<String> agents, List<Rule> rules) {
        boolean matches(String userAgent) {
            return agents.stream().anyMatch(agent -> "*".equals(agent) || userAgent.contains(agent));
        }

        int specificity(String userAgent) {
            return agents.stream().filter(agent -> "*".equals(agent) || userAgent.contains(agent))
                    .mapToInt(agent -> "*".equals(agent) ? 0 : agent.length()).max().orElse(0);
        }
    }

    private record Rule(boolean allow, Pattern pattern, int specificity) {
        static Rule of(boolean allow, String value) {
            boolean endAnchored = value.endsWith("$");
            String raw = endAnchored ? value.substring(0, value.length() - 1) : value;
            String regex = List.of(raw.split("\\*", -1)).stream()
                    .map(Pattern::quote).collect(Collectors.joining(".*"));
            return new Rule(allow, Pattern.compile("^" + regex + (endAnchored ? "$" : ".*")), raw.replace("*", "").length());
        }

        boolean matches(String path) {
            return pattern.matcher(path).matches();
        }
    }
}
