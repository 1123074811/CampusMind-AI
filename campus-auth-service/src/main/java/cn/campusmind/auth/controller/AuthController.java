package cn.campusmind.auth.controller;

import cn.campusmind.auth.application.AuthService;
import cn.campusmind.auth.application.PasswordRecoveryService;
import cn.campusmind.auth.application.SsoCodeService;
import cn.campusmind.auth.config.SessionCookieProperties;
import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "campusmind_access";
    private static final String REFRESH_COOKIE = "campusmind_refresh";
    private final AuthService authService;
    private final SsoCodeService ssoCodeService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final SessionCookieProperties cookieProperties;

    public AuthController(AuthService authService, SsoCodeService ssoCodeService,
                          PasswordRecoveryService passwordRecoveryService,
                          SessionCookieProperties cookieProperties) {
        this.authService = authService;
        this.ssoCodeService = ssoCodeService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/password/forgot")
    public ApiResponse<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ApiResponse.ok(passwordRecoveryService.request(request));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.reset(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/sso/exchange")
    public ApiResponse<LoginResponse> exchangeSsoCode(@Valid @RequestBody SsoExchangeRequest request) {
        return ApiResponse.ok(ssoCodeService.consume(request.code()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @RequestBody(required = false) LogoutRequest request) {
        authService.logout(authorization, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/web/login")
    public ResponseEntity<ApiResponse<WebSessionResponse>> webLogin(@Valid @RequestBody LoginRequest request) {
        return cookieSession(authService.login(request));
    }

    @PostMapping("/web/refresh")
    public ResponseEntity<ApiResponse<WebSessionResponse>> webRefresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        requireCookie(refreshToken, "刷新会话不存在");
        return cookieSession(authService.refresh(refreshToken));
    }

    @GetMapping("/web/session")
    public ApiResponse<WebSessionResponse> webSession(
            @CookieValue(value = ACCESS_COOKIE, required = false) String accessToken) {
        requireCookie(accessToken, "登录会话不存在");
        return ApiResponse.ok(WebSessionResponse.from(authService.current(accessToken)));
    }

    @PostMapping("/web/logout")
    public ResponseEntity<ApiResponse<Void>> webLogout(
            @CookieValue(value = ACCESS_COOKIE, required = false) String accessToken,
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        if (StringUtils.hasText(accessToken)) {
            authService.logout("Bearer " + accessToken, new LogoutRequest(refreshToken));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie(ACCESS_COOKIE, "/").toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie(REFRESH_COOKIE, "/api/v1/auth").toString())
                .body(ApiResponse.ok(null));
    }

    private ResponseEntity<ApiResponse<WebSessionResponse>> cookieSession(LoginResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, response.accessToken(), "/", response.expiresAt()).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, response.refreshToken(), "/api/v1/auth", response.refreshExpiresAt()).toString())
                .body(ApiResponse.ok(WebSessionResponse.from(response)));
    }

    private ResponseCookie cookie(String name, String value, String path, Instant expiresAt) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(cookieProperties.secure())
                .sameSite("Strict").path(path)
                .maxAge(Duration.between(Instant.now(), expiresAt).isNegative() ? Duration.ZERO : Duration.between(Instant.now(), expiresAt))
                .build();
    }

    private ResponseCookie clearCookie(String name, String path) {
        return ResponseCookie.from(name, "").httpOnly(true).secure(cookieProperties.secure())
                .sameSite("Strict").path(path).maxAge(Duration.ZERO).build();
    }

    private static void requireCookie(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
        }
    }
}
