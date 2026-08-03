package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NovelVersionService 版本原子激活契约测试（Mockito）。
 */
@ExtendWith(MockitoExtension.class)
class NovelVersionServiceTest {

    private static final String NOVEL_ID = "n1";
    private static final String VERSION_TAG = "v1";
    private static final String COLLECTION_NAME = VectorStore.collectionNameFor(NOVEL_ID, VERSION_TAG);

    @Mock
    private NovelVersionRepository novelVersionRepository;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private VectorStore vectorStore;

    private NovelVersionService novelVersionService;

    @BeforeEach
    void setUp() {
        novelVersionService = new NovelVersionService(
                novelVersionRepository, novelRepository, sceneRepository, vectorStore);
    }

    @Test
    void activateSuccessSetsActivePointerAndDowngradesOldActive() {
        // Given: target 版本 EMBED_DONE + 向量集合就绪
        NovelVersion target = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag(VERSION_TAG)
                .status(VersionStatus.EMBED_DONE)
                .build();
        NovelVersion oldActive = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag("v0")
                .status(VersionStatus.ACTIVE)
                .build();
        NovelVersion otherDone = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag("v-other")
                .status(VersionStatus.EMBED_DONE)
                .build();
        Novel novel = Novel.builder().id(NOVEL_ID).title("Test Novel").build();

        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG)).thenReturn(Optional.of(target));
        when(novelVersionRepository.findByNovelId(NOVEL_ID)).thenReturn(List.of(target, oldActive, otherDone));
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(true);
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));

        // When
        novelVersionService.activate(NOVEL_ID, VERSION_TAG);

        // Then: target → ACTIVE, oldActive → EMBED_DONE, otherDone 不变
        assertThat(target.getStatus()).isEqualTo(VersionStatus.ACTIVE);
        assertThat(target.getActivatedAt()).isNotNull();
        assertThat(target.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(oldActive.getStatus()).isEqualTo(VersionStatus.EMBED_DONE);
        assertThat(otherDone.getStatus()).isEqualTo(VersionStatus.EMBED_DONE);
        assertThat(novel.getActiveVersionTag()).isEqualTo(VERSION_TAG);

        verify(novelVersionRepository).save(target);
        verify(novelVersionRepository).save(oldActive);
        verify(novelVersionRepository, never()).save(otherDone);
        verify(novelRepository).save(novel);
    }

    @Test
    void rejectWhenNotEmbedDone() {
        NovelVersion target = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag(VERSION_TAG)
                .status(VersionStatus.EMBEDDING)
                .build();
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> novelVersionService.activate(NOVEL_ID, VERSION_TAG))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMBED_DONE");

        verify(novelVersionRepository, never()).save(any());
        verify(novelRepository, never()).save(any());
    }

    @Test
    void rejectWhenVersionNotFound() {
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> novelVersionService.activate(NOVEL_ID, VERSION_TAG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(VERSION_TAG);

        verify(novelVersionRepository, never()).save(any());
        verify(novelRepository, never()).save(any());
    }

    @Test
    void rejectWhenCollectionMissing() {
        NovelVersion target = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag(VERSION_TAG)
                .status(VersionStatus.EMBED_DONE)
                .build();
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG)).thenReturn(Optional.of(target));
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(false);

        assertThatThrownBy(() -> novelVersionService.activate(NOVEL_ID, VERSION_TAG))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("集合");

        verify(novelVersionRepository, never()).save(any());
        verify(novelRepository, never()).save(any());
    }

    @Test
    void collectNameIsSetBeforeActivate() {
        NovelVersion target = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag(VERSION_TAG)
                .status(VersionStatus.EMBED_DONE)
                .collectionName(null)
                .build();
        Novel novel = Novel.builder().id(NOVEL_ID).title("Test").build();

        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG)).thenReturn(Optional.of(target));
        when(novelVersionRepository.findByNovelId(NOVEL_ID)).thenReturn(List.of(target));
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(true);
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));

        novelVersionService.activate(NOVEL_ID, VERSION_TAG);

        assertThat(target.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(target.getStatus()).isEqualTo(VersionStatus.ACTIVE);
    }
}
