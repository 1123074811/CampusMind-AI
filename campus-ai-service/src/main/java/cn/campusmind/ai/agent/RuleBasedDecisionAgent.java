package cn.campusmind.ai.agent;

import cn.campusmind.ai.agent.rules.DecisionRules;
import cn.campusmind.ai.domain.SearchPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 规则版决策 Agent。当 {@code campus.ai.mode=rule}（默认）时启用，
 * 委托 {@link DecisionRules} 用关键词匹配识别意图并生成检索计划。
 */
@Service
@ConditionalOnProperty(name = "campus.ai.mode", havingValue = "rule", matchIfMissing = true)
public class RuleBasedDecisionAgent implements DecisionAgent {

    @Override
    public SearchPlan plan(String query, List<String> userScopes, boolean usePersonalProfile) {
        return DecisionRules.plan(query, userScopes, usePersonalProfile);
    }
}
