package cn.campusmind.importing.feign;

import cn.campusmind.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "campus-user-service", contextId = "userPrivacyFeignClient")
public interface UserPrivacyFeignClient {

    @GetMapping("/api/v1/users/me/privacy")
    ApiResponse<PrivacyStatus> privacy();

    record PrivacyStatus(String currentPolicyVersion, int retentionDays, List<Consent> consents) { }

    record Consent(Long id, String consentType, String policyVersion, boolean granted,
                   String source, List<String> scopes, String occurredAt) { }
}
