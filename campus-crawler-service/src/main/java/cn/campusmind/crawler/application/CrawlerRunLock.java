package cn.campusmind.crawler.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrawlerRunLock {

    private static final String PREFIX = "crawler:source-lock:";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Set<Long> localLocks = ConcurrentHashMap.newKeySet();

    public CrawlerRunLock(ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.redisTemplate = redisTemplate.getIfAvailable();
    }

    public Lease tryAcquire(Long sourceId) {
        String token = UUID.randomUUID().toString();
        if (redisTemplate == null) {
            return localLocks.add(sourceId) ? new Lease(sourceId, token) : null;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(PREFIX + sourceId, token, TTL);
        return Boolean.TRUE.equals(acquired) ? new Lease(sourceId, token) : null;
    }

    public void release(Lease lease) {
        if (redisTemplate == null) {
            localLocks.remove(lease.sourceId());
            return;
        }
        redisTemplate.execute(RELEASE, List.of(PREFIX + lease.sourceId()), lease.token());
    }

    public record Lease(Long sourceId, String token) {
    }
}
