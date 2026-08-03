package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NovelVersion 生命周期状态机与游标行为测试。
 */
class NovelVersionTest {

    private NovelVersion version;

    @BeforeEach
    void setUp() {
        version = NovelVersion.builder()
                .novelId("novel-1")
                .versionTag("v1")
                .splitStrategy(SplitStrategy.SCENE_BOUNDARY)
                .chunkSize(500)
                .chunkOverlap(50)
                .status(VersionStatus.PENDING)
                .createdAt(1000L)
                .updatedAt(1000L)
                .build();
    }

    // ---------- startSplit ----------

    @Test
    void startSplitFromPendingTransitionsToSplittingAndTouchesUpdatedAt() {
        long before = version.getUpdatedAt();
        version.startSplit();
        assertEquals(VersionStatus.SPLITTING, version.getStatus());
        assertTrue(version.getUpdatedAt() >= before);
    }

    @Test
    void startSplitIsIdempotentWhenAlreadySplitting() {
        version.startSplit();
        long updatedAtAfterFirstCall = version.getUpdatedAt();
        version.startSplit();
        assertEquals(VersionStatus.SPLITTING, version.getStatus());
        assertEquals(updatedAtAfterFirstCall, version.getUpdatedAt());
    }

    @Test
    void startSplitRejectsTerminalActiveStatus() {
        version.setStatus(VersionStatus.ACTIVE);
        assertThrows(IllegalStateException.class, version::startSplit);
    }

    @Test
    void startSplitRejectsTerminalAbandonedStatus() {
        version.setStatus(VersionStatus.ABANDONED);
        assertThrows(IllegalStateException.class, version::startSplit);
    }

    @Test
    void startSplitAllowedFromSplitDone() {
        version.setStatus(VersionStatus.SPLIT_DONE);
        version.startSplit();
        assertEquals(VersionStatus.SPLITTING, version.getStatus());
    }

    // ---------- completeSplit ----------

    @Test
    void completeSplitOnlyFromSplitting() {
        version.setStatus(VersionStatus.SPLITTING);
        version.completeSplit();
        assertEquals(VersionStatus.SPLIT_DONE, version.getStatus());
    }

    @Test
    void completeSplitRejectsNonSplitting() {
        version.setStatus(VersionStatus.PENDING);
        assertThrows(IllegalStateException.class, version::completeSplit);
    }

    // ---------- startEmbed ----------

    @Test
    void startEmbedFromSplitDone() {
        version.setStatus(VersionStatus.SPLIT_DONE);
        version.startEmbed();
        assertEquals(VersionStatus.EMBEDDING, version.getStatus());
    }

    @Test
    void startEmbedFromFailed() {
        version.setStatus(VersionStatus.FAILED);
        version.startEmbed();
        assertEquals(VersionStatus.EMBEDDING, version.getStatus());
    }

    @Test
    void startEmbedRejectsInvalidStatus() {
        version.setStatus(VersionStatus.PENDING);
        assertThrows(IllegalStateException.class, version::startEmbed);
    }

    // ---------- completeEmbed ----------

    @Test
    void completeEmbedOnlyFromEmbedding() {
        version.setStatus(VersionStatus.EMBEDDING);
        version.completeEmbed();
        assertEquals(VersionStatus.EMBED_DONE, version.getStatus());
    }

    @Test
    void completeEmbedRejectsNonEmbedding() {
        version.setStatus(VersionStatus.SPLIT_DONE);
        assertThrows(IllegalStateException.class, version::completeEmbed);
    }

    // ---------- activate ----------

    @Test
    void activateOnlyFromEmbedDoneAndSetsActivatedAt() {
        version.setStatus(VersionStatus.EMBED_DONE);
        version.activate();
        assertEquals(VersionStatus.ACTIVE, version.getStatus());
        assertNotNull(version.getActivatedAt());
    }

    @Test
    void activateRejectsNonEmbedDone() {
        version.setStatus(VersionStatus.EMBEDDING);
        assertThrows(IllegalStateException.class, version::activate);
    }

    // ---------- fail / abandon ----------

    @Test
    void failTransitionsToFailed() {
        version.setStatus(VersionStatus.SPLITTING);
        version.fail();
        assertEquals(VersionStatus.FAILED, version.getStatus());
    }

    @Test
    void abandonTransitionsToAbandonedAndSetsAbandonedAt() {
        version.setStatus(VersionStatus.SPLIT_DONE);
        version.abandon();
        assertEquals(VersionStatus.ABANDONED, version.getStatus());
        assertNotNull(version.getAbandonedAt());
    }

    // ---------- advanceSplitCursor ----------

    @Test
    void advanceSplitCursorUpdatesBothCursorFields() {
        version.advanceSplitCursor(7, 42L);
        assertEquals(7, version.getSplitCursorChapterIndex());
        assertEquals(42L, version.getSplitCursorSceneSeq());
    }

    // ---------- isStalled ----------

    @Test
    void isStalledTrueWhenSplittingBeyondThreshold() {
        version.setStatus(VersionStatus.SPLITTING);
        version.setUpdatedAt(1000L);
        assertTrue(version.isStalled(2000L, 500L));
    }

    @Test
    void isStalledFalseWhenSplittingWithinThreshold() {
        version.setStatus(VersionStatus.SPLITTING);
        version.setUpdatedAt(1000L);
        assertFalse(version.isStalled(1200L, 500L));
    }

    @Test
    void isStalledFalseWhenNotSplittingOrEmbedding() {
        version.setStatus(VersionStatus.SPLIT_DONE);
        version.setUpdatedAt(1000L);
        assertFalse(version.isStalled(5000L, 0L));
    }
}
