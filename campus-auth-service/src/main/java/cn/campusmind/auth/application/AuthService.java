package cn.campusmind.auth.application;

import cn.campusmind.auth.controller.LoginRequest;
import cn.campusmind.auth.controller.LoginResponse;
import cn.campusmind.auth.controller.LogoutRequest;
import cn.campusmind.auth.controller.RefreshTokenRequest;
import cn.campusmind.auth.domain.UserAccount;
import cn.campusmind.auth.domain.UserStatus;
import cn.campusmind.auth.infrastructure.mapper.UserAccountMapper;
import cn.campusmind.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import io.jsonwebtoken.Claims;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthSessionService authSessionService;

    public AuthService(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuthSessionService authSessionService
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authSessionService = authSessionService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username()));
        if (user == null) {
            throw invalidCredentials();
        }

        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("USER_DISABLED", "账号已被禁用", HttpStatus.FORBIDDEN);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return issueSession(user, authSessionService.create(user.getId()));
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(RefreshTokenRequest request) {
        AuthSessionService.Session session = authSessionService.rotate(request.refreshToken());
        UserAccount user = userAccountMapper.selectById(session.userId());
        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("USER_DISABLED", "账号不存在或已被禁用", HttpStatus.FORBIDDEN);
        }
        return issueSession(user, session);
    }

    @Transactional(readOnly = true)
    public LoginResponse loginSso(String username) {
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username));
        if (user == null) {
            throw new BusinessException("SSO_USER_NOT_PROVISIONED", "学校账号尚未开通 CampusMind 权限", HttpStatus.FORBIDDEN);
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("USER_DISABLED", "账号已被禁用", HttpStatus.FORBIDDEN);
        }
        return issueSession(user, authSessionService.create(user.getId()));
    }

    public void logout(String authorization, LogoutRequest request) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("UNAUTHORIZED", "缺少访问令牌", HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Claims claims = jwtTokenService.parse(token);
        authSessionService.revoke(
                claims.getId(),
                claims.getExpiration().toInstant(),
                request == null ? null : request.refreshToken()
        );
    }

    private LoginResponse issueSession(UserAccount user, AuthSessionService.Session session) {
        JwtTokenService.TokenIssue tokenIssue = jwtTokenService.issueAccessToken(user, session.sessionId());
        return new LoginResponse(
                tokenIssue.token(),
                "Bearer",
                tokenIssue.expiresAt(),
                session.refreshToken(),
                session.refreshExpiresAt(),
                new LoginResponse.UserPrincipal(user.getId(), user.getUsername(), user.getRole().name())
        );
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
    }
}
