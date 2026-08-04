package com.novel.splitter.application.service.ingest;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestRollbackServiceTest {

    @Mock private NovelRepository novelRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private NovelCacheRepository novelCacheRepository;

    @InjectMocks
    private IngestRollbackService ingestRollbackService;

    @Test
    void rollback_deletesFilesChaptersAndNovelRow() {
        when(novelRepository.findById("n1"))
                .thenReturn(Optional.of(Novel.builder().id("n1").build()));

        ingestRollbackService.rollback("n1");

        verify(novelCacheRepository).removeNovelArtifacts("n1");
        verify(chapterRepository).deleteByNovelId("n1");
        verify(novelRepository).hardDelete("n1");
    }

    @Test
    void rollback_isIdempotent_whenNovelMissing() {
        when(novelRepository.findById("n1")).thenReturn(Optional.empty());

        ingestRollbackService.rollback("n1");

        verify(novelCacheRepository, never()).removeNovelArtifacts(anyString());
        verify(chapterRepository, never()).deleteByNovelId(anyString());
        verify(novelRepository, never()).hardDelete(anyString());
    }

    @Test
    void rollback_blankNovelId_isNoOp() {
        assertThatCode(() -> ingestRollbackService.rollback("  "))
                .doesNotThrowAnyException();
        verify(novelRepository, never()).findById(anyString());
    }

    @Test
    void rollback_nullNovelId_isNoOp() {
        assertThatCode(() -> ingestRollbackService.rollback(null))
                .doesNotThrowAnyException();
        verify(novelRepository, never()).findById(anyString());
    }
}
