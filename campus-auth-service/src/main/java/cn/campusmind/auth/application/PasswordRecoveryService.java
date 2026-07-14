package cn.campusmind.auth.application;

import cn.campusmind.auth.config.PasswordRecoveryProperties;
import cn.campusmind.auth.controller.ForgotPasswordRequest;
import cn.campusmind.auth.controller.ForgotPasswordResponse;
import cn.campusmind.auth.controller.ResetPasswordRequest;
import cn.campusmind.auth.domain.UserAccount;
import cn.campusmind.auth.infrastructure.mapper.UserAccountMapper;
import cn.campusmind.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

@Service
public class PasswordRecoveryService {

    private static final String KEY_PREFIX = "auth:password-reset:";
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final PasswordRecoveryProperties properties;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordRecoveryService(UserAccountMapper userAccountMapper,
                                   StringRedisTemplate redisTemplate,
                                   PasswordEncoder passwordEncoder,
                                   AuthSessionService authSessionService,
                                   PasswordRecoveryProperties properties,
                                   ObjectProvider<JavaMailSender> mailSender) {
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.properties = properties;
        this.mailSender = mailSender.getIfAvailable();
    }

    public ForgotPasswordResponse request(ForgotPasswordRequest request) {
        String rawAccount = request.account().trim();
        String emailAccount = rawAccount.toLowerCase(Locale.ROOT);
        // 必须用 nested 包住 OR，避免后续条件扩展时运算符优先级把查询放大。
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .nested(w -> w.eq(UserAccount::getUsername, rawAccount).or().eq(UserAccount::getEmail, emailAccount)));
        String token = null;
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            token = randomToken();
            redisTemplate.opsForValue().set(KEY_PREFIX + hash(token), String.valueOf(user.getId()),
                    Duration.ofMinutes(Math.max(5, properties.tokenTtlMinutes())));
            if (properties.mailEnabled()) {
                sendResetEmail(user.getEmail(), token);
            }
        }
        return new ForgotPasswordResponse(true, properties.exposeToken() ? token : null);
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + hash(request.token()));
        if (value == null) {
            throw new BusinessException("INVALID_RESET_TOKEN", "重置链接无效或已过期", HttpStatus.BAD_REQUEST);
        }
        Long userId = Long.parseLong(value);
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("INVALID_RESET_TOKEN", "重置链接无效或已过期", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountMapper.updateById(user);
        authSessionService.revokeUserSessions(userId);
    }

    private void sendResetEmail(String email, String token) {
        if (mailSender == null) {
            throw new BusinessException("MAIL_NOT_CONFIGURED", "密码找回邮件服务未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject("CampusMind 密码重置");
        message.setText("请在有效期内打开以下链接完成密码重置：\n" + properties.resetUrl() + "?token=" + token);
        mailSender.send(message);
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
