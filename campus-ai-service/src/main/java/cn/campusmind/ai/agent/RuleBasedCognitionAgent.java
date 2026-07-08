package cn.campusmind.ai.agent;

import cn.campusmind.ai.agent.rules.CognitionRules;
import cn.campusmind.ai.domain.CampusEventCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 规则版认知 Agent。当 {@code campus.ai.mode=rule}（默认）时启用，
 * 委托 {@link CognitionRules} 用正则与关键词抽取事件字段。
 */
@Service
@ConditionalOnProperty(name = "campus.ai.mode", havingValue = "rule", matchIfMissing = true)
public class RuleBasedCognitionAgent implements CognitionAgent {

    @Override
    public CampusEventCandidate extract(String sourceType, String plainText) {
        return CognitionRules.extract(sourceType, plainText);
    }
}
