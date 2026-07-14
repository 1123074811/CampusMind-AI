package cn.campusmind.auth.application;

import cn.campusmind.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    @Test
    void refreshTokenIsSingleUseAndRotated() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("42:old-session");
        AuthSessionService service = new AuthSessionService(redis,
                new AuthProperties("issuer", "test-secret-key-with-at-least-32-bytes", 15, 30));

        AuthSessionService.Session session = service.rotate("old-refresh-token");

        assertThat(session.userId()).isEqualTo(42L);
        assertThat(session.sessionId()).isNotEqualTo("old-session");
        assertThat(session.refreshToken()).isNotBlank();
        verify(values).set(anyString(), anyString(), any(Duration.class));
    }
}
