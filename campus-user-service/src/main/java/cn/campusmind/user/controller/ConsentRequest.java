package cn.campusmind.user.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConsentRequest(
        @NotBlank @Pattern(regexp = "PRIVACY_POLICY|PERSONALIZATION|NOTIFICATION") String consentType,
        boolean granted,
        @NotBlank @Size(max = 32) String policyVersion,
        @Size(max = 32) String source
) { }
