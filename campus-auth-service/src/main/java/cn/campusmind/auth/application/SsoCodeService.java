package cn.campusmind.auth.application;

import cn.campusmind.auth.controller.LoginResponse;
import cn.campusmind.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class SsoCodeService {
    private static final String PREFIX = "auth:sso-code:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SsoCodeService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String create(LoginResponse response) {
        try {
            String code = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(PREFIX + code, objectMapper.writeValueAsString(response), Duration.ofSeconds(60));
            return code;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("SSO response serialization failed", ex);
        }
    }

    public LoginResponse consume(String code) {
        String json = redisTemplate.opsForValue().getAndDelete(PREFIX + code);
        if (json == null) {
            throw new BusinessException("INVALID_SSO_CODE", "单点登录兑换码无效或已过期", HttpStatus.UNAUTHORIZED);
        }
        try {
            return objectMapper.readValue(json, LoginResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("SSO response deserialization failed", ex);
        }
    }
}
