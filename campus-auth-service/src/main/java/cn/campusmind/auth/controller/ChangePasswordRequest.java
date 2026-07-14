package cn.campusmind.auth.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(min = 6, max = 128) String currentPassword,
        @NotBlank @Size(min = 6, max = 128) String newPassword
) { }
