package cn.campusmind.audit.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewEventRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|REVIEWED|REJECTED|CORRECTED|OFFLINE")
        String status,

        @Size(max = 512)
        String comment
) {
}
