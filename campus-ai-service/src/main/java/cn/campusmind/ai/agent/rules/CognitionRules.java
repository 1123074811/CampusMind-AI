package cn.campusmind.ai.agent.rules;

import cn.campusmind.ai.domain.CampusEventCandidate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 认知抽取的规则实现。从非结构化文本中用正则与关键词匹配抽取校园事件字段。
 *
 * <p>独立于 Spring 上下文存在，{@link cn.campusmind.ai.agent.RuleBasedCognitionAgent}
 * 与 {@link cn.campusmind.ai.agent.LlmCognitionAgent} 的降级路径都复用本类，
 * 避免降级时依赖规则版 bean（不同 mode 下 bean 注册情况不同）。
 */
public final class CognitionRules {

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)|(\\d{1,2}月\\d{1,2}日)");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?:地点|地址)[:：]?\\s*([^，。\\n；;]+)");
    private static final Pattern ORGANIZER_PATTERN = Pattern.compile("(?:主办|组织者|发布单位|举办单位)[:：]?\\s*([^，。\\n；;]+)");
    private static final Pattern SCOPE_PATTERN = Pattern.compile("(?:面向|对象|范围)[:：]?\\s*([^，。\\n；;]+)");

    private CognitionRules() {
    }

    public static CampusEventCandidate extract(String sourceType, String plainText) {
        String normalizedText = normalize(plainText);
        String title = extractTitle(normalizedText);
        String eventType = detectEventType(normalizedText);
        String startTime = extractStartTime(normalizedText);
        String location = matchFirst(LOCATION_PATTERN, normalizedText);
        String organizer = matchFirst(ORGANIZER_PATTERN, normalizedText);
        List<String> scopes = splitList(matchFirst(SCOPE_PATTERN, normalizedText));
        List<String> tags = detectTags(normalizedText, eventType);
        boolean needReview = startTime == null || location == null || title.length() < 6 || normalizedText.length() < 20;
        double confidence = calculateConfidence(startTime, location, organizer, tags, needReview);
        String summary = summarize(title, startTime, location, scopes);
        String reason = needReview ? "时间、地点或内容完整性不足，需要人工复核" : "规则抽取字段较完整，可进入AI预测发布";

        return new CampusEventCandidate(
                title,
                eventType,
                summary,
                startTime,
                null,
                location,
                organizer,
                scopes,
                tags,
                confidence,
                needReview,
                reason
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }

    private static String extractTitle(String text) {
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
            }
        }
        return "未命名校园事件";
    }

    private static String detectEventType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(text, "考试", "期末", "补考", "考场")) {
            return "EXAM";
        }
        if (containsAny(text, "作业", "提交", "截止", "雨课堂")) {
            return "HOMEWORK";
        }
        if (containsAny(text, "课程", "上课", "调课", "停课")) {
            return "COURSE";
        }
        if (containsAny(text, "讲座", "报告", "论坛", "沙龙") || lower.contains("ai")) {
            return "LECTURE";
        }
        if (containsAny(text, "竞赛", "比赛", "大赛")) {
            return "COMPETITION";
        }
        if (containsAny(text, "活动", "报名", "社团")) {
            return "ACTIVITY";
        }
        if (containsAny(text, "服务", "缴费", "维修", "图书馆")) {
            return "SERVICE";
        }
        if (containsAny(text, "通知", "公告")) {
            return "NOTICE";
        }
        return "OTHER";
    }

    private static String extractStartTime(String text) {
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (!dateMatcher.find()) {
            return null;
        }
        String date = normalizeDate(dateMatcher.group());
        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        if (timeMatcher.find()) {
            return date + "T" + timeMatcher.group() + ":00+08:00";
        }
        return date + "T00:00:00+08:00";
    }

    private static String normalizeDate(String rawDate) {
        String cleaned = rawDate.replace("年", "-").replace("月", "-").replace("日", "")
                .replace("/", "-").replace(".", "-");
        String[] parts = cleaned.split("-");
        if (parts.length == 2) {
            return LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static String matchFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Set<String> items = new LinkedHashSet<>();
        for (String item : value.split("[,，、/ ]+")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank()) {
                items.add(trimmed);
            }
        }
        return List.copyOf(items);
    }

    private static List<String> detectTags(String text, String eventType) {
        List<String> tags = new ArrayList<>();
        tags.add(eventType);
        if (text.toLowerCase(Locale.ROOT).contains("ai") || text.contains("人工智能")) {
            tags.add("AI");
        }
        if (text.contains("软件学院")) {
            tags.add("软件学院");
        }
        if (text.contains("报名")) {
            tags.add("报名");
        }
        return tags.stream().distinct().toList();
    }

    private static double calculateConfidence(String startTime, String location, String organizer, List<String> tags, boolean needReview) {
        double score = 0.45;
        if (startTime != null) {
            score += 0.2;
        }
        if (location != null) {
            score += 0.15;
        }
        if (organizer != null) {
            score += 0.1;
        }
        if (!tags.isEmpty()) {
            score += 0.05;
        }
        if (needReview) {
            score -= 0.1;
        }
        return Math.max(0.1, Math.min(0.95, Math.round(score * 100.0) / 100.0));
    }

    private static String summarize(String title, String startTime, String location, List<String> scopes) {
        StringBuilder summary = new StringBuilder(title);
        if (startTime != null) {
            summary.append("，时间").append(startTime);
        }
        if (location != null) {
            summary.append("，地点").append(location);
        }
        if (!scopes.isEmpty()) {
            summary.append("，面向").append(String.join("、", scopes));
        }
        String value = summary.toString();
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
