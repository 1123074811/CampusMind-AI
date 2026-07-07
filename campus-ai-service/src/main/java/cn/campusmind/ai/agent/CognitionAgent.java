package cn.campusmind.ai.agent;

import cn.campusmind.ai.domain.CampusEventCandidate;

public interface CognitionAgent {

    CampusEventCandidate extract(String sourceType, String plainText);
}
