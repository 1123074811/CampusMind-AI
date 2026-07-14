package cn.campusmind.auth.application;

import cn.campusmind.auth.config.AuthProperties;
import cn.campusmind.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthSessionService {

    public static final String REVOKED_SESSION_PREFIX = "auth:revoked:";
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Session create(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = randomToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(properties.refreshTokenTtlDays()));
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + hash(refreshToken),
                userId + ":" + sessionId,
                Duration.ofDays(properties.refreshTokenTtlDays())
        );
        return new Session(userId, sessionId, refreshToken, expiresAt);
    }

    public Session rotate(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + hash(refreshToken);
        String stored = redisTemplate.opsForValue().getAndDelete(key);
        if (stored == null) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效或已过期", HttpStatus.UNAUTHORIZED);
        }
        String[] values = stored.split(":", 2);
        return create(Long.parseLong(values[0]));
    }

    public void revoke(String sessionId, Instant accessTokenExpiresAt, String refreshToken) {
        if (sessionId != null && accessTokenExpiresAt != null) {
            Duration ttl = Duration.between(Instant.now(), accessTokenExpiresAt);
            if (!ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(REVOKED_SESSION_PREFIX + sessionId, "1", ttl);
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + hash(refreshToken));
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Session(Long userId, String sessionId, String refreshToken, Instant refreshExpiresAt) {
    }
}
