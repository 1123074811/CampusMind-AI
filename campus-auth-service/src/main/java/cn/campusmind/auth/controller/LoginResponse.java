package cn.campusmind.auth.controller;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt,
        UserPrincipal user
) {

    public record UserPrincipal(
            Long id,
            String username,
            String role
    ) {
    }
}
