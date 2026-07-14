package cn.campusmind.auth.controller;

import cn.campusmind.auth.application.AuthService;
import cn.campusmind.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final cn.campusmind.auth.application.SsoCodeService ssoCodeService;

    public AuthController(AuthService authService, cn.campusmind.auth.application.SsoCodeService ssoCodeService) {
        this.authService = authService;
        this.ssoCodeService = ssoCodeService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/sso/exchange")
    public ApiResponse<LoginResponse> exchangeSsoCode(@Valid @RequestBody SsoExchangeRequest request) {
        return ApiResponse.ok(ssoCodeService.consume(request.code()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(required = false) LogoutRequest request
    ) {
        authService.logout(authorization, request);
        return ApiResponse.ok(null);
    }
}
