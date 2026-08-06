package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class SceneExpanderTest {

    private SceneRepository repo;
    private SceneExpander expander;
    private AssemblerConfig config;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SceneRepository.class);
        expander = new SceneExpander(repo);
        config = new AssemblerConfig();
        config.setExpandRadius(1);
        config.setExpandAcrossChapters(false);
    }

    private Scene scene(String id, long seq, int chapter, double score) {
        return Scene.builder()
                .id(id).seq(seq).chapterIndex(chapter).score(score)
                .metadata(SceneMetadata.builder()
                        .novel("n1").version("v1").chunkSize(478).chunkOverlap(65)
                        .build())
                .build();
    }

    @Test
    void expand_appendsNeighborsWithDecayedScore() {
        Scene anchor = scene("s2", 2L, 1, 0.8);
        Scene n1 = scene("s1", 1L, 1, 0.0);
        Scene n3 = scene("s3", 3L, 1, 0.0);
        when(repo.findByProfileAndSeqRange("n1", "v1", 478, 65, 1L, 3L))
                .thenReturn(List.of(n1, anchor, n3));

        List<Scene> out = expander.expand(List.of(anchor), config);

        assertEquals(3, out.size());
        assertEquals("s2", out.get(0).getId());
        assertEquals("s1", out.get(1).getId());
        assertEquals(0.8 * 0.9, out.get(1).getScore(), 1e-9);
        assertEquals("s3", out.get(2).getId());
        assertEquals(0.8 * 0.9, out.get(2).getScore(), 1e-9);
    }

    @Test
    void expand_deduplicatesExistingIds() {
        Scene anchor = scene("s2", 2L, 1, 0.8);
        when(repo.findByProfileAndSeqRange("n1", "v1", 478, 65, 1L, 3L))
                .thenReturn(List.of(anchor));

        List<Scene> out = expander.expand(List.of(anchor), config);

        assertEquals(1, out.size());
    }

    @Test
    void expand_radiusDisabled_returnsInput() {
        config.setExpandRadius(-1);
        List<Scene> out = expander.expand(List.of(scene("s2", 2L, 1, 0.8)), config);
        assertEquals(1, out.size());
    }

    @Test
    void expand_skipsCrossChapterWhenDisabled() {
        Scene anchor = scene("s2", 2L, 1, 0.8);
        Scene cross = scene("s3", 3L, 2, 0.0);
        when(repo.findByProfileAndSeqRange("n1", "v1", 478, 65, 1L, 3L))
                .thenReturn(List.of(anchor, cross));

        List<Scene> out = expander.expand(List.of(anchor), config);

        assertEquals(1, out.size());
    }
}
