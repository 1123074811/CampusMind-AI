package cn.campusmind.auth.controller;

import java.time.Instant;

public record WebSessionResponse(
        Instant expiresAt,
        Instant refreshExpiresAt,
        LoginResponse.UserPrincipal user
) {
    public static WebSessionResponse from(LoginResponse response) {
        return new WebSessionResponse(response.expiresAt(), response.refreshExpiresAt(), response.user());
    }
}
