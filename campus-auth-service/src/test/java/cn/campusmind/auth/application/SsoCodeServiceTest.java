package cn.campusmind.auth.application;

import cn.campusmind.auth.controller.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SsoCodeServiceTest {

    @Test
    void oneTimeCodeReturnsStoredLoginResponse() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        AtomicReference<String> stored = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        org.mockito.Mockito.doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(java.time.Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(invocation -> stored.getAndSet(null));
        SsoCodeService service = new SsoCodeService(redis, new ObjectMapper().findAndRegisterModules());
        LoginResponse response = new LoginResponse("access", "Bearer", Instant.now().plusSeconds(60),
                "refresh", Instant.now().plusSeconds(120), new LoginResponse.UserPrincipal(1L, "alice", "STUDENT"));

        String code = service.create(response);
        LoginResponse restored = service.consume(code);

        assertThat(restored.accessToken()).isEqualTo("access");
        assertThat(restored.user().username()).isEqualTo("alice");
    }
}
