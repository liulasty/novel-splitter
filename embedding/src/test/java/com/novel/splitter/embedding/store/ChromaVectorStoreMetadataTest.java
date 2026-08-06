package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChromaVectorStoreMetadataTest {

    private ChromaVectorStore store;

    @BeforeEach
    void setUp() {
        store = new ChromaVectorStore(RestClient.builder(), "http://localhost:1",
                "test-collection", "cosine", false, false, 1, 1);
    }

    @Test
    void buildChromaMetadata_includesStructuredKeysWhenPresent() {
        Scene s = Scene.builder()
                .id("s1").persistenceId(1L)
                .metadata(SceneMetadata.builder()
                        .novel("n1").version("v1").chunkSize(478).chunkOverlap(65)
                        .sequenceNum(1)
                        .role("dialogue").location("乌坦城").time("夜晚")
                        .characters(List.of("萧炎", "药老"))
                        .build())
                .build();

        Map<String, Object> m = store.buildChromaMetadata(s);

        assertEquals("dialogue", m.get("role"));
        assertEquals("乌坦城", m.get("location"));
        assertEquals("夜晚", m.get("time"));
        assertEquals(List.of("萧炎", "药老"), m.get("characters"));
    }

    @Test
    void buildChromaMetadata_omitsNullStructuredKeys() {
        Scene s = Scene.builder()
                .id("s1").persistenceId(1L)
                .metadata(SceneMetadata.builder()
                        .novel("n1").version("v1").chunkSize(478).chunkOverlap(65)
                        .sequenceNum(1)
                        .build())
                .build();

        Map<String, Object> m = store.buildChromaMetadata(s);

        assertFalse(m.containsKey("role"));
        assertFalse(m.containsKey("location"));
        assertFalse(m.containsKey("time"));
        assertFalse(m.containsKey("characters"));
        assertEquals("n1", m.get("novelId"));
    }
}
