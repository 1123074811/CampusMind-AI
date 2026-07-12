package cn.campusmind.ai.agent.rules;

import cn.campusmind.ai.domain.SearchPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 决策意图识别与检索计划生成的规则实现。用关键词匹配识别用户查询意图，
 * 输出结构化 {@link SearchPlan}。
 *
 * <p>独立于 Spring 上下文，{@link cn.campusmind.ai.agent.RuleBasedDecisionAgent}
 * 与 {@link cn.campusmind.ai.agent.LlmDecisionAgent} 的降级路径都复用本类。
 */
public final class DecisionRules {

    private DecisionRules() {
    }

    public static SearchPlan plan(String query, List<String> userScopes, boolean usePersonalProfile) {
        String text = query == null ? "" : query.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> eventTypes = detectEventTypes(text, lower);
        String intent = detectIntent(text, lower, eventTypes, usePersonalProfile);
        boolean vectorSearch = "SEMANTIC_SEARCH".equals(intent) || "QA_EXPLAIN".equals(intent) || lower.contains("相关");
        boolean personalProfile = usePersonalProfile || "PERSONAL_SCHEDULE".equals(intent);

        return new SearchPlan(
                intent,
                eventTypes,
                detectTimeRange(text),
                userScopes == null ? List.of() : userScopes,
                vectorSearch,
                personalProfile,
                vectorSearch ? 10 : 20
        );
    }

    private static String detectIntent(String text, String lower, List<String> eventTypes, boolean usePersonalProfile) {
        if (isCasualChat(text, lower)) {
            return "CASUAL_CHAT";
        }
        if (containsAny(text, "怎么导入", "如何导入", "cookie", "json")) {
            return "IMPORT_HELP";
        }
        if (containsAny(text, "什么意思", "解释", "说明一下")) {
            return "QA_EXPLAIN";
        }
        if (usePersonalProfile || containsAny(text, "我的", "本周有哪些作业", "我的课表")) {
            return "PERSONAL_SCHEDULE";
        }
        if (lower.contains("ai") || containsAny(text, "相关", "类似", "有没有")) {
            return "SEMANTIC_SEARCH";
        }
        if (!eventTypes.isEmpty() || containsAny(text, "今天", "明天", "本周", "通知")) {
            return "FEED_QUERY";
        }
        return "SEMANTIC_SEARCH";
    }

    private static boolean isCasualChat(String text, String lower) {
        if (text.length() > 20) return false;
        return containsAny(text,
                "你好", "您好", "hello", "hi", "嗨", "哈喽",
                "谢谢", "感谢", "好的", "嗯", "哦", "了解",
                "再见", "拜拜", "在吗", "在不在",
                "你是谁", "你能做什么", "你叫什么");
    }

    private static List<String> detectEventTypes(String text, String lower) {
        List<String> types = new ArrayList<>();
        addIf(types, text, "考试", "EXAM");
        addIf(types, text, "作业", "HOMEWORK");
        addIf(types, text, "课程", "COURSE");
        addIf(types, text, "讲座", "LECTURE");
        addIf(types, text, "竞赛", "COMPETITION");
        addIf(types, text, "活动", "ACTIVITY");
        if (lower.contains("ai") && !types.contains("LECTURE")) {
            types.add("LECTURE");
        }
        return types;
    }

    private static String detectTimeRange(String text) {
        if (text.contains("今天")) {
            return "TODAY";
        }
        if (text.contains("明天")) {
            return "TOMORROW";
        }
        if (text.contains("本周") || text.contains("这周")) {
            return "THIS_WEEK";
        }
        if (text.contains("最近")) {
            return "RECENT";
        }
        return "ANY";
    }

    private static void addIf(List<String> types, String text, String keyword, String type) {
        if (text.contains(keyword)) {
            types.add(type);
        }
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
