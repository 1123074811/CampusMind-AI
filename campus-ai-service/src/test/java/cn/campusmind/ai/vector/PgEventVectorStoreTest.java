package cn.campusmind.ai.vector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgEventVectorStoreTest {

    @Test
    void exposesSemanticModeAndDoesNotHideStoreFailuresAsEmptyResults() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("pg unavailable"));
        PgEventVectorStore store = new PgEventVectorStore(vectorStore);

        assertThat(store.retrievalMode()).isEqualTo("SEMANTIC");
        assertThat(store.fallback()).isFalse();
        assertThatThrownBy(() -> store.search("讲座", 10, 1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
