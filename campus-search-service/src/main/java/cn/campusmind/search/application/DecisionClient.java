package cn.campusmind.search.application;

import java.util.List;

/**
 * 决策客户端：调用 campus-ai-service 的 /api/v1/ai/decision/plan 识别意图并生成检索计划。
 */
public interface DecisionClient {

    DecisionPlan plan(String query, List<String> userScopes, boolean usePersonalProfile);
}
