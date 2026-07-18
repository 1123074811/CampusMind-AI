package cn.campusmind.ai.application;

import cn.campusmind.ai.domain.SearchPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);
    private static final int MAX_CONVERSATIONS = 500;
    private static final int MAX_TURNS = 6;
    private static final String KEY_PREFIX = "ai:chat:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    // ponytail: Redis 故障时仅保证当前实例最近 500 个会话；恢复后新消息重新进入共享存储。
    private final Map<String, List<ConversationTurn>> local = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<ConversationTurn>> eldest) {
            return size() > MAX_CONVERSATIONS;
        }
    };

    public ConversationMemory(ObjectProvider<StringRedisTemplate> redisProvider,
                              ObjectMapper objectMapper,
                              @Value("${campus.ai.memory.short-term-ttl:PT24H}") Duration ttl) {
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public List<ConversationTurn> history(String conversationKey) {
        if (redis != null) {
            try {
                List<String> values = redis.opsForList().range(key(conversationKey), -MAX_TURNS, -1);
                if (values != null) {
                    return values.stream().map(this::readTurn).toList();
                }
            } catch (RuntimeException ex) {
                log.debug("Redis chat history unavailable, using local memory", ex);
            }
        }
        synchronized (local) {
            return List.copyOf(local.getOrDefault(conversationKey, List.of()));
        }
    }

    public void remember(String conversationKey, ConversationTurn turn) {
        ConversationTurn stored = new ConversationTurn(
                turn.userMessage(), truncate(turn.assistantAnswer(), 2000), turn.plan());
        if (redis != null) {
            try {
                String redisKey = key(conversationKey);
                redis.opsForList().rightPush(redisKey, objectMapper.writeValueAsString(stored));
                redis.opsForList().trim(redisKey, -MAX_TURNS, -1);
                redis.expire(redisKey, ttl);
                if (!conversationKey.startsWith("anonymous:")) {
                    String indexKey = userIndexKey(conversationKey.substring(0, conversationKey.indexOf(':')));
                    redis.opsForSet().add(indexKey, redisKey);
                    redis.expire(indexKey, ttl);
                }
                return;
            } catch (Exception ex) {
                log.debug("Redis chat write unavailable, using local memory", ex);
            }
        }
        synchronized (local) {
            List<ConversationTurn> updated = new ArrayList<>(local.getOrDefault(conversationKey, List.of()));
            updated.add(stored);
            if (updated.size() > MAX_TURNS) {
                updated = new ArrayList<>(updated.subList(updated.size() - MAX_TURNS, updated.size()));
            }
            local.put(conversationKey, List.copyOf(updated));
        }
    }

    public void forgetUser(Long userId) {
        if (userId == null) {
            return;
        }
        if (redis != null) {
            try {
                String indexKey = userIndexKey(userId.toString());
                Set<String> keys = redis.opsForSet().members(indexKey);
                if (keys != null && !keys.isEmpty()) {
                    redis.delete(keys);
                }
                redis.delete(indexKey);
            } catch (RuntimeException ex) {
                log.debug("Redis chat deletion unavailable", ex);
            }
        }
        synchronized (local) {
            local.keySet().removeIf(key -> key.startsWith(userId + ":"));
        }
    }

    private ConversationTurn readTurn(String value) {
        try {
            return objectMapper.readValue(value, ConversationTurn.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid chat memory", ex);
        }
    }

    private static String key(String conversationKey) {
        return KEY_PREFIX + conversationKey;
    }

    private static String userIndexKey(String userId) {
        return KEY_PREFIX + "user:" + userId;
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    public record ConversationTurn(String userMessage, String assistantAnswer, SearchPlan plan) {
    }
}
