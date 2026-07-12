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
    private static final String DATE_TIME_VALUE = "((?:20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?|\\d{1,2}月\\d{1,2}日)(?:\\s*\\d{1,2}:\\d{2})?)";
    private static final Pattern REGISTRATION_START_PATTERN = Pattern.compile("(?:报名开始时间|报名起始时间|报名时间)[:：]?\\s*" + DATE_TIME_VALUE);
    private static final Pattern REGISTRATION_DEADLINE_PATTERN = Pattern.compile("(?:报名截止时间|报名截止|截止时间|截止日期)[:：]?\\s*" + DATE_TIME_VALUE);
    private static final Pattern DURATION_PATTERN = Pattern.compile("(?:比赛时间|竞赛时间|活动时间|持续时间)[:：]?\\s*([^。\\n；;]+)");
    private static final Pattern MATERIALS_PATTERN = Pattern.compile("(?:所需材料|报名材料|提交材料)[:：]?\\s*([^。\\n；;]+)");
    private static final Pattern PARTICIPATION_PATTERN = Pattern.compile("(?:参赛方式|报名方式|参与方式)[:：]?\\s*([^。\\n；;]+)");
    private static final Pattern TEAM_PATTERN = Pattern.compile("(?:组队要求|团队要求)[:：]?\\s*([^。\\n；;]+)");
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("附件(?:\\d+)?[:：]?\\s*([^。\\n；;]+)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s，。；;]+", Pattern.CASE_INSENSITIVE);

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
        List<String> keyDates = extractKeyDates(normalizedText);
        List<String> actions = detectActions(normalizedText);
        boolean registrationEvent = "COMPETITION".equals(eventType) || "ACTIVITY".equals(eventType);
        String registrationStart = registrationEvent ? matchFirst(REGISTRATION_START_PATTERN, normalizedText) : null;
        String registrationDeadline = registrationEvent ? matchFirst(REGISTRATION_DEADLINE_PATTERN, normalizedText) : null;
        String duration = registrationEvent ? matchFirst(DURATION_PATTERN, normalizedText) : null;
        List<String> materials = registrationEvent ? splitList(matchFirst(MATERIALS_PATTERN, normalizedText)) : List.of();
        String registrationUrl = registrationEvent ? matchUrl(normalizedText) : null;
        String participationMethod = registrationEvent ? matchFirst(PARTICIPATION_PATTERN, normalizedText) : null;
        String teamRequirement = registrationEvent ? matchFirst(TEAM_PATTERN, normalizedText) : null;
        List<String> attachments = splitList(matchFirst(ATTACHMENT_PATTERN, normalizedText));
        boolean competitionIncomplete = "COMPETITION".equals(eventType)
                && (registrationDeadline == null || registrationUrl == null);
        boolean needReview = startTime == null || title.length() < 6 || normalizedText.length() < 20 || competitionIncomplete;
        String summary = summarize(title, startTime, location, scopes);
        String reason = needReview ? "关键字段或内容完整性不足，需要人工复核" : "规则抽取字段较完整，可生成精简卡片";

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
                needReview,
                reason,
                null,
                null,
                keyDates,
                actions,
                registrationStart,
                registrationDeadline,
                duration,
                materials,
                registrationUrl,
                participationMethod,
                teamRequirement,
                attachments
        );
    }

    private static List<String> extractKeyDates(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            values.add(normalizeDate(matcher.group()));
        }
        return List.copyOf(values);
    }

    private static List<String> detectActions(String text) {
        List<String> actions = new ArrayList<>();
        if (text.contains("报名")) actions.add("完成报名");
        if (text.contains("提交") || text.contains("材料")) actions.add("提交所需材料");
        if (text.contains("缴费")) actions.add("完成缴费");
        if (text.contains("签到") || text.contains("到场")) actions.add("按时到场");
        return actions.stream().distinct().toList();
    }

    private static String matchUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
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
        if (containsAny(text, "竞赛", "比赛", "大赛")) {
            return "COMPETITION";
        }
        if (containsAny(text, "作业", "课程作业", "课后作业")
                || (text.contains("雨课堂") && containsAny(text, "提交", "截止"))) {
            return "HOMEWORK";
        }
        if (containsAny(text, "课程", "上课", "调课", "停课")) {
            return "COURSE";
        }
        if (containsAny(text, "讲座", "专题报告", "论坛", "沙龙")
                || (text.contains("学术报告") && !text.contains("学术报告厅")) || lower.contains("ai")) {
            return "LECTURE";
        }
        if (containsAny(text, "招聘", "招募", "录用", "拟聘", "考核")) {
            return "NOTICE";
        }
        if (containsAny(text, "会议", "动员会", "座谈会", "仪式")) {
            return "ACTIVITY";
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
