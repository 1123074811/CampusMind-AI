package cn.campusmind.ai.application;

import cn.campusmind.ai.application.ConversationMemory.ConversationTurn;
import cn.campusmind.ai.domain.SearchPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryTest {

    private static final SearchPlan PLAN =
            new SearchPlan("SEMANTIC_SEARCH", List.of(), "ANY", List.of(), true, false, 5);

    @Test
    void storesSixTurnsInLocalFallback() {
        ConversationMemory memory = localMemory();
        for (int i = 1; i <= 7; i++) {
            memory.remember("7:session", new ConversationTurn("q" + i, "a" + i, PLAN));
        }

        assertThat(memory.history("7:session"))
                .extracting(ConversationTurn::userMessage)
                .containsExactly("q2", "q3", "q4", "q5", "q6", "q7");
        assertThat(memory.history("8:session")).isEmpty();
        memory.forgetUser(7L);
        assertThat(memory.history("7:session")).isEmpty();
    }

    @Test
    void usesRedisListForSharedMemory() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> lists = mock(ListOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> sets = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ConversationTurn turn = new ConversationTurn("问题", "回答", PLAN);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForList()).thenReturn(lists);
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.members("ai:chat:user:7")).thenReturn(Set.of("ai:chat:7:session"));
        when(lists.range("ai:chat:7:session", -6, -1))
                .thenReturn(List.of(mapper.writeValueAsString(turn)));
        ConversationMemory memory = new ConversationMemory(provider, mapper, Duration.ofHours(24));

        assertThat(memory.history("7:session")).containsExactly(turn);
        memory.remember("7:session", turn);

        verify(lists).rightPush(org.mockito.ArgumentMatchers.eq("ai:chat:7:session"), anyString());
        verify(lists).trim("ai:chat:7:session", -6, -1);
        verify(redis).expire("ai:chat:7:session", Duration.ofHours(24));
        verify(sets).add("ai:chat:user:7", "ai:chat:7:session");
        verify(redis).expire("ai:chat:user:7", Duration.ofHours(24));

        memory.forgetUser(7L);
        verify(redis).delete(Set.of("ai:chat:7:session"));
        verify(redis).delete("ai:chat:user:7");
    }

    private static ConversationMemory localMemory() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new ConversationMemory(
                provider, new ObjectMapper().findAndRegisterModules(), Duration.ofHours(24));
    }
}
