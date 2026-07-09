package cn.campusmind.user.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(max = 64)
        String username,

        @Size(max = 32)
        String phone,

        @NotBlank
        @Pattern(regexp = "ADMIN|OPERATOR|STUDENT")
        String role,

        @NotBlank
        @Size(min = 6, max = 64)
        String password
) {
}
