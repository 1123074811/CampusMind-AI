package cn.campusmind.user.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConsentRequest(
        @NotBlank @Pattern(regexp = "PRIVACY_POLICY|PERSONALIZATION|NOTIFICATION|ACADEMIC_DATA_IMPORT") String consentType,
        boolean granted,
        @NotBlank @Size(max = 32) String policyVersion,
        @Size(max = 32) String source,
        @Size(max = 3) List<@Pattern(regexp = "TIMETABLE|EXAM|HOMEWORK") String> scopes
) { }
