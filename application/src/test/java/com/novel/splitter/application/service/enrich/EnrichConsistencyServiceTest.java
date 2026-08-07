package com.novel.splitter.application.service.enrich;

import com.novel.splitter.domain.exception.BusinessException;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EnrichConsistencyServiceTest {

    private final SceneRepository repo = Mockito.mock(SceneRepository.class);
    private final EnrichConsistencyService svc = new EnrichConsistencyService(repo);

    @Test
    void ensureEmbeddable_allowsZero() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(100L);
        when(repo.countEnrichedByNovelIdAndVersion("n", "v")).thenReturn(0L);
        assertDoesNotThrow(() -> svc.ensureEmbeddable("n", "v"));
    }

    @Test
    void ensureEmbeddable_allowsHundred() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(100L);
        when(repo.countEnrichedByNovelIdAndVersion("n", "v")).thenReturn(100L);
        assertDoesNotThrow(() -> svc.ensureEmbeddable("n", "v"));
    }

    @Test
    void ensureEmbeddable_rejectsIntermediate() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(100L);
        when(repo.countEnrichedByNovelIdAndVersion("n", "v")).thenReturn(57L);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.ensureEmbeddable("n", "v"));
        assertEquals(5003, ex.getErrorCode().getCode());
        assertTrue(ex.getMessage().contains("57"));
    }

    @Test
    void progress_noScenes_isZero() {
        when(repo.countActiveByNovelIdAndVersion("n", "v")).thenReturn(0L);
        assertEquals(0, svc.progress("n", "v"));
    }
}
