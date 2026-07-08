package cn.campusmind.importing.application;

import cn.campusmind.importing.config.ImportProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 雨课堂 Cookie 临时存储：仅写 Redis，短 TTL，不落库。任务结束或到期自动清除。
 */
@Service
public class RainCookieStore {

    private static final String KEY_PREFIX = "rain:session:";

    private final StringRedisTemplate redisTemplate;
    private final ImportProperties properties;

    public RainCookieStore(StringRedisTemplate redisTemplate, ImportProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void save(String sessionId, String cookie) {
        redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, cookie,
                Duration.ofMinutes(properties.rainCookieTtlMinutes()));
    }

    public void delete(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }
}
