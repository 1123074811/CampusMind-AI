package cn.campusmind.user.controller;

import java.time.LocalDateTime;
import java.util.List;

public record PrivacyStatusResponse(String currentPolicyVersion, int retentionDays, List<Consent> consents) {
    public record Consent(Long id, String consentType, String policyVersion, boolean granted,
                          String source, LocalDateTime occurredAt) { }
}
