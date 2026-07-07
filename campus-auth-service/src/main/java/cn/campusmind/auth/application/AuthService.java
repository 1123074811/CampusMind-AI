package cn.campusmind.auth.application;

import cn.campusmind.auth.controller.LoginRequest;
import cn.campusmind.auth.controller.LoginResponse;
import cn.campusmind.auth.domain.UserAccount;
import cn.campusmind.auth.domain.UserStatus;
import cn.campusmind.auth.infrastructure.mapper.UserAccountMapper;
import cn.campusmind.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
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

        JwtTokenService.TokenIssue tokenIssue = jwtTokenService.issueAccessToken(user);
        return new LoginResponse(
                tokenIssue.token(),
                "Bearer",
                tokenIssue.expiresAt(),
                new LoginResponse.UserPrincipal(user.getId(), user.getUsername(), user.getRole().name())
        );
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
    }
}
