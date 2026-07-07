package cn.campusmind.ai.agent;

import cn.campusmind.ai.domain.SearchPlan;

import java.util.List;

public interface DecisionAgent {

    SearchPlan plan(String query, List<String> userScopes, boolean usePersonalProfile);
}
