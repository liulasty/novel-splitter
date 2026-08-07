package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SplitNovelUseCase 每章 checkpoint 续传契约：
 * <ul>
 *   <li>重复从 0 执行产出完全一致的 seq 集合（配合唯一约束无副作用）；</li>
 *   <li>中断后从游标 (lastChapterIndex+1, lastSceneSeq) 续传 → 两次产出 seq 无重叠且拼接连续；</li>
 *   <li>lastSceneSeq == 全部有效场景数。</li>
 * </ul>
 *
 * <p>夹具：3 章、每章段落总长 &lt; 350 字 → 每章恰好 1 个有效场景（全局 seq 1..3）。</p>
 */
class SplitNovelUseCaseResumeTest {

    private static final int CHAPTER_COUNT = 3;

    @Test
    void repeatedRunFromZeroProducesIdenticalSeqSets() {
        RecordingSceneRepository repo = new RecordingSceneRepository();
        SplitNovelUseCase uc = newUsecase(repo);

        SplitNovelUseCase.SplitProgress first = uc.split("t1", "n1", "novel", -1, "v1", 350, 65, 0, 0L, null);
        List<Long> firstSeqs = repo.lastSeqCall();
        SplitNovelUseCase.SplitProgress second = uc.split("t2", "n1", "novel", -1, "v1", 350, 65, 0, 0L, null);
        List<Long> secondSeqs = repo.lastSeqCall();

        assertEquals(3, first.sceneIds().size());
        assertEquals(List.of(1L, 2L, 3L), firstSeqs);
        assertEquals(firstSeqs, secondSeqs, "重复执行应产出完全一致的 seq 集合");
    }

    @Test
    void resumeFromCursorContinuesWithoutOverlap() {
        RecordingSceneRepository repo = new RecordingSceneRepository();
        SplitNovelUseCase uc = newUsecase(repo);

        // 第一次跑：maxScenes=1 使第 0 章成为最后一次贡献场景的章节（模拟中断，游标 = 第 0 章末）
        SplitNovelUseCase.SplitProgress p1 = uc.split("t1", "n1", "novel", 1, "v1", 350, 65, 0, 0L, null);
        assertEquals(0, p1.lastChapterIndex());
        assertEquals(1L, p1.lastSceneSeq());
        List<Long> p1Seqs = repo.seqCalls().get(0);
        assertEquals(List.of(1L), p1Seqs);

        // 第二次跑：从游标 (lastChapterIndex+1, lastSceneSeq) 续传
        SplitNovelUseCase.SplitProgress p2 = uc.split("t2", "n1", "novel", -1, "v1", 350, 65,
                p1.lastChapterIndex() + 1, p1.lastSceneSeq(), null);
        List<Long> p2Seqs = repo.seqCalls().get(1);

        assertEquals(List.of(2L, 3L), p2Seqs, "续传应从 startSceneSeq+1 起连续分配");
        assertTrue(Collections.disjoint(p1Seqs, p2Seqs), "两次产出的 seq 不应重叠");
        List<Long> combined = new ArrayList<>(p1Seqs);
        combined.addAll(p2Seqs);
        assertEquals(List.of(1L, 2L, 3L), combined, "两次产出拼接后应连续无缝隙");
        assertEquals(3L, p2.lastSceneSeq());
    }

    @Test
    void lastSceneSeqEqualsTotalValidScenes() {
        RecordingSceneRepository repo = new RecordingSceneRepository();
        SplitNovelUseCase uc = newUsecase(repo);

        SplitNovelUseCase.SplitProgress full = uc.split("t1", "n1", "novel", -1, "v1", 350, 65, 0, 0L, null);

        assertEquals((long) full.sceneIds().size(), full.lastSceneSeq(),
                "lastSceneSeq 应等于全部有效场景数");
    }

    // === fixtures ===

    private SplitNovelUseCase newUsecase(RecordingSceneRepository sceneRepo) {
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < CHAPTER_COUNT; i++) {
            chapters.add(Chapter.builder()
                    .index(i)
                    .title("第" + (i + 1) + "章")
                    .startParagraphIndex(0)
                    .endParagraphIndex(2)
                    .build());
        }

        NovelCacheRepository cacheRepo = new NovelCacheRepository() {
            private UnsupportedOperationException unsupported() {
                return new UnsupportedOperationException("not used");
            }

            @Override
            public ChapterData loadChapter(String novelId, int chapterIndex) {
                return ChapterData.builder()
                        .chapter(chapters.get(chapterIndex))
                        .paragraphs(paragraphs(chapterIndex))
                        .build();
            }

            @Override public Path rawOriginalPath(String novelId) { throw unsupported(); }
            @Override public Path rawDirPath(String novelId) { throw unsupported(); }
            @Override public Path parsedDirPath(String novelId) { throw unsupported(); }
            @Override public Path parsedChapterPath(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public void saveChapter(String novelId, int chapterIndex, ChapterData chapterData) { throw unsupported(); }
            @Override public Stream<Path> listChapterFiles(String novelId) { throw unsupported(); }
            @Override public InputStream openChapterInputStream(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public OutputStream openChapterOutputStream(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public void removeParsedArtifacts(String novelId) { throw unsupported(); }
            @Override public void removeNovelArtifacts(String novelId) { throw unsupported(); }
        };

        ChapterRepository chapterRepo = new ChapterRepository() {
            @Override public List<Chapter> findByNovelId(String novelId) { return chapters; }
            @Override public void saveAll(List<Chapter> chapters) { throw new UnsupportedOperationException("not used"); }
            @Override public boolean existsByNovelId(String novelId) { throw new UnsupportedOperationException("not used"); }
            @Override public void deleteByNovelId(String novelId) { throw new UnsupportedOperationException("not used"); }
        };

        return new SplitNovelUseCase(cacheRepo, sceneRepo, chapterRepo);
    }

    /** 每章 3 段、总长远小于滑窗 350 → 恰好 1 个场景。 */
    private List<RawParagraph> paragraphs(int chapterIndex) {
        return List.of(
                RawParagraph.builder().index(chapterIndex * 10).content("第一章正文内容用来生成一个场景。").isEmpty(false).build(),
                RawParagraph.builder().index(chapterIndex * 10 + 1).content("这里继续叙述故事情节的发展脉络。").isEmpty(false).build(),
                RawParagraph.builder().index(chapterIndex * 10 + 2).content("至此一段完整的场景就此结束。").isEmpty(false).build()
        );
    }

    /** 记录每次 saveScenesIdempotent 收到的 seq 集合，返回递增 id；其余仓库方法不触发。 */
    private static class RecordingSceneRepository implements SceneRepository {
        private final List<List<Long>> seqCalls = new ArrayList<>();
        private long idSeq = 0L;

        List<List<Long>> seqCalls() {
            return seqCalls;
        }

        List<Long> lastSeqCall() {
            return seqCalls.isEmpty() ? List.of() : seqCalls.get(seqCalls.size() - 1);
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used");
        }

        @Override
        public List<Long> saveScenesIdempotent(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes) {
            List<Long> seqs = scenes.stream().map(Scene::getSeq).collect(Collectors.toList());
            seqCalls.add(seqs);
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < scenes.size(); i++) {
                ids.add(++idSeq);
            }
            return ids;
        }

        @Override public List<Long> saveScenes(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes) { throw unsupported(); }
        @Override public long maxSeqByVersion(String novelId, String version) { return 0L; }
        @Override public List<Scene> findByIds(List<Long> ids) { throw unsupported(); }
        @Override public List<Scene> findBySceneIds(List<String> sceneIds) { throw unsupported(); }
        @Override public void deleteNovelById(String novelId) { throw unsupported(); }
        @Override public void deleteByProfile(String novelId, String version, int chunkSize, int chunkOverlap) { throw unsupported(); }
        @Override public void deleteAll() { throw unsupported(); }
        @Override public List<SceneSplitProfile> listSplitProfilesByNovelId(String novelId) { throw unsupported(); }
        @Override public List<Scene> findAllByNovelId(String novelId) { throw unsupported(); }
        @Override public List<Scene> findAllByNovelIdAndVersion(String novelId, String version) { throw unsupported(); }
        @Override public List<Scene> findByProfile(String novelId, String version, int chunkSize, int chunkOverlap) { throw unsupported(); }
        @Override public List<Long> listPersistenceIdsByProfile(String novelId, String version, int chunkSize, int chunkOverlap) { throw unsupported(); }
        @Override public long countByProfile(String novelId, String version, int chunkSize, int chunkOverlap) { throw unsupported(); }
        @Override public long countAllByNovelIdAndVersion(String novelId, String version) { throw unsupported(); }
        @Override public long countActiveByNovelIdAndVersion(String novelId, String version) { return 0L; }
        @Override public long countEnrichedByNovelIdAndVersion(String novelId, String version) { return 0L; }
        @Override public int clearEnrichMetadata(String novelId, String version) { return 0; }
        @Override public PagedResult<Scene> findLightweightScenes(PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByNovelId(String novelId, PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByProfile(String novelId, String version, int chunkSize, int chunkOverlap, PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId, PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, PageQuery pageQuery) { throw unsupported(); }
        @Override public List<SceneCountByProfile> countScenesByNovelVersionAndChunk() { throw unsupported(); }
        @Override public int resetEmbedStateForRun(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId) { throw unsupported(); }
        @Override public void updateEmbedOutcome(Long persistenceId, String embedRunId, EmbedStatus status, String embedError) { throw unsupported(); }
        @Override public void batchUpdateEmbedOutcome(List<Long> persistenceIds, String embedRunId, EmbedStatus status, String embedError) { throw unsupported(); }
        @Override public void updateScenesMetadata(List<Scene> scenes) { throw unsupported(); }
        @Override public long countEmbedByRunAndStatus(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId, EmbedStatus status) { throw unsupported(); }
        @Override public List<Long> listPersistenceIdsForEmbedResume(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId) { throw unsupported(); }
        @Override public Optional<int[]> resolveChunkProfileForEmbedRun(String novelId, String version, String embedRunId) { throw unsupported(); }
        @Override public List<Scene> findByProfileAndSeqRange(String novelId, String version, int chunkSize, int chunkOverlap, long fromSeq, long toSeq) { throw unsupported(); }
    }
}
