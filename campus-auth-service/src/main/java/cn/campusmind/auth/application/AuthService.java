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
import cn.campusmind.auth.controller.RegisterRequest;

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
        return refresh(request.refreshToken());
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        AuthSessionService.Session session = authSessionService.rotate(refreshToken);
        UserAccount user = userAccountMapper.selectById(session.userId());
        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("USER_DISABLED", "账号不存在或已被禁用", HttpStatus.FORBIDDEN);
        }
        return issueSession(user, session);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        Long duplicates = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .nested(w -> w.eq(UserAccount::getUsername, username).or().eq(UserAccount::getEmail, email)));
        if (duplicates > 0) {
            throw new BusinessException("ACCOUNT_EXISTS", "用户名或邮箱已注册", HttpStatus.CONFLICT);
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(cn.campusmind.auth.domain.UserRole.STUDENT);
        user.setStatus(1);
        userAccountMapper.insert(user);
        return issueSession(user, authSessionService.create(user.getId()));
    }

    @Transactional(readOnly = true)
    public LoginResponse current(String accessToken) {
        Claims claims = jwtTokenService.parse(accessToken);
        UserAccount user = userAccountMapper.selectById(Long.parseLong(claims.getSubject()));
        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("USER_DISABLED", "账号不存在或已被禁用", HttpStatus.FORBIDDEN);
        }
        return new LoginResponse(accessToken, "Bearer", claims.getExpiration().toInstant(), null, null,
                new LoginResponse.UserPrincipal(user.getId(), user.getUsername(), user.getRole().name()));
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

    @Transactional
    public void changePassword(String authorization, cn.campusmind.auth.controller.ChangePasswordRequest request) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("UNAUTHORIZED", "缺少访问令牌", HttpStatus.UNAUTHORIZED);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与当前密码相同", HttpStatus.BAD_REQUEST);
        }
        Claims claims = jwtTokenService.parse(authorization.substring("Bearer ".length()).trim());
        Long userId = Long.parseLong(claims.getSubject());
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException("USER_DISABLED", "账号不存在或已被禁用", HttpStatus.FORBIDDEN);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "当前密码不正确", HttpStatus.UNAUTHORIZED);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountMapper.updateById(user);
        authSessionService.revokeUserSessions(userId);
        authSessionService.revoke(claims.getId(), claims.getExpiration().toInstant(), null);
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
