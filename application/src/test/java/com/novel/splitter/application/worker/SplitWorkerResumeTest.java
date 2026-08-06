package com.novel.splitter.application.worker;

import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.pipeline.model.ResolvedChunkingParams;
import com.novel.splitter.pipeline.orchestrator.SplitNovelUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SplitWorker 游标续传契约（Mockito 装配，真实 {@link SplitNovelUseCase} + 内存版仓库）：
 * <ul>
 *   <li>同一版本连续两次投递不重复落库（第二次从游标续传，无新增写入）；</li>
 *   <li>预置游标后以 (lastChapterIndex+1, lastSceneSeq) 续传；</li>
 *   <li>版本行不存在时自动创建（PENDING，chunk 参数来自解析后的有效值）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SplitWorkerResumeTest {

    private static final String NOVEL_ID = "n1";
    private static final String VERSION = "v1";
    private static final int CHUNK = 350;
    private static final int OVERLAP = 65;

    @Mock
    private TaskService taskService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private EmbedPipelineOrchestrator embedPipelineOrchestrator;
    @Mock
    private NovelService novelService;

    @Test
    void reprocessingSameVersionDoesNotDuplicateScenes() {
        RecordingSceneRepository sceneRepo = new RecordingSceneRepository();
        SplitNovelUseCase realUseCase = new SplitNovelUseCase(resumeCacheRepo(), sceneRepo, resumeChapterRepo());
        InMemoryNovelVersionRepository versionRepo = new InMemoryNovelVersionRepository();
        SplitWorker worker = new SplitWorker(
                realUseCase, taskService, rabbitTemplate, embedPipelineOrchestrator, novelService, versionRepo);
        stubCommon();

        worker.processSplitTask(msg("t1"));
        worker.processSplitTask(msg("t2"));

        // 第二次从游标续传，无新增写入 → 全部有效 seq 恰为 1..3，绝不重复
        assertThat(sceneRepo.allSavedSeqs()).containsExactly(1L, 2L, 3L);
        assertThat(sceneRepo.seqCalls()).hasSize(1);

        NovelVersion v = versionRepo.findById(NOVEL_ID, VERSION).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.SPLIT_DONE);
        assertThat(v.getSplitCursorChapterIndex()).isEqualTo(2);
        assertThat(v.getSplitCursorSceneSeq()).isEqualTo(3L);
    }

    @Test
    void resumeContinuesFromSavedCursor() {
        SplitNovelUseCase useCase = mock(SplitNovelUseCase.class);
        when(useCase.resolveChunkingParams(any(), any())).thenReturn(new ResolvedChunkingParams(CHUNK, OVERLAP));
        when(useCase.split(anyString(), eq(NOVEL_ID), any(), anyInt(), eq(VERSION), any(), any(),
                eq(1), eq(2L), any())).thenReturn(new SplitNovelUseCase.SplitProgress(List.of(4L, 5L), 2, 4L));

        InMemoryNovelVersionRepository versionRepo = new InMemoryNovelVersionRepository();
        NovelVersion preset = NovelVersion.builder()
                .novelId(NOVEL_ID).versionTag(VERSION)
                .splitStrategy(SplitStrategy.OVERLAP_CHUNK)
                .chunkSize(CHUNK).chunkOverlap(OVERLAP)
                .status(VersionStatus.SPLITTING)
                .splitCursorChapterIndex(0)
                .splitCursorSceneSeq(2L)
                .createdAt(System.currentTimeMillis()).updatedAt(System.currentTimeMillis())
                .build();
        versionRepo.save(preset);
        SplitWorker worker = new SplitWorker(
                useCase, taskService, rabbitTemplate, embedPipelineOrchestrator, novelService, versionRepo);
        stubCommon();

        worker.processSplitTask(msg("t3"));

        verify(useCase).split(anyString(), eq(NOVEL_ID), any(), anyInt(), eq(VERSION), any(), any(),
                eq(1), eq(2L), any());
        NovelVersion v = versionRepo.findById(NOVEL_ID, VERSION).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.SPLIT_DONE);
        assertThat(v.getSplitCursorChapterIndex()).isEqualTo(2);
        assertThat(v.getSplitCursorSceneSeq()).isEqualTo(4L);
    }

    @Test
    void versionMissingCreatesRow() {
        SplitNovelUseCase useCase = mock(SplitNovelUseCase.class);
        when(useCase.resolveChunkingParams(any(), any())).thenReturn(new ResolvedChunkingParams(CHUNK, OVERLAP));
        when(useCase.split(anyString(), eq(NOVEL_ID), any(), anyInt(), eq(VERSION), any(), any(),
                eq(0), eq(0L), any())).thenReturn(new SplitNovelUseCase.SplitProgress(List.of(1L, 2L, 3L), 2, 3L));

        InMemoryNovelVersionRepository versionRepo = new InMemoryNovelVersionRepository();
        SplitWorker worker = new SplitWorker(
                useCase, taskService, rabbitTemplate, embedPipelineOrchestrator, novelService, versionRepo);
        stubCommon();

        worker.processSplitTask(msg("t4"));

        verify(useCase).split(anyString(), eq(NOVEL_ID), any(), anyInt(), eq(VERSION), any(), any(),
                eq(0), eq(0L), any());
        NovelVersion v = versionRepo.findById(NOVEL_ID, VERSION).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.SPLIT_DONE);
        assertThat(v.getChunkSize()).isEqualTo(CHUNK);
        assertThat(v.getChunkOverlap()).isEqualTo(OVERLAP);
        assertThat(v.getSplitStrategy()).isEqualTo(SplitStrategy.OVERLAP_CHUNK);
        assertThat(v.getSplitCursorChapterIndex()).isEqualTo(2);
        assertThat(v.getSplitCursorSceneSeq()).isEqualTo(3L);
    }

    // === fixtures ===

    private void stubCommon() {
        when(taskService.getTask(anyString())).thenAnswer(inv -> new SplitTask(
                inv.getArgument(0), TaskType.SCENE_SPLIT, NOVEL_ID, "demo.txt", -1, VERSION));
        when(novelService.getNovelById(NOVEL_ID)).thenReturn(Novel.builder().id(NOVEL_ID).title("测试小说").build());
    }

    private SplitTaskMessage msg(String taskId) {
        SplitTaskMessage m = new SplitTaskMessage(taskId, NOVEL_ID, 0, VERSION);
        m.setChunkSize(CHUNK);
        m.setChunkOverlap(OVERLAP);
        return m;
    }

    private static final int CHAPTER_COUNT = 3;

    private NovelCacheRepository resumeCacheRepo() {
        List<Chapter> chapters = resumeChapters();
        return new NovelCacheRepository() {
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
    }

    private ChapterRepository resumeChapterRepo() {
        List<Chapter> chapters = resumeChapters();
        return new ChapterRepository() {
            @Override public List<Chapter> findByNovelId(String novelId) { return chapters; }
            @Override public void saveAll(List<Chapter> chapters) { throw new UnsupportedOperationException("not used"); }
            @Override public boolean existsByNovelId(String novelId) { throw new UnsupportedOperationException("not used"); }
            @Override public void deleteByNovelId(String novelId) { throw new UnsupportedOperationException("not used"); }
        };
    }

    private List<Chapter> resumeChapters() {
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < CHAPTER_COUNT; i++) {
            chapters.add(Chapter.builder()
                    .index(i)
                    .title("第" + (i + 1) + "章")
                    .startParagraphIndex(0)
                    .endParagraphIndex(2)
                    .build());
        }
        return chapters;
    }

    private List<RawParagraph> paragraphs(int chapterIndex) {
        return List.of(
                RawParagraph.builder().index(chapterIndex * 10).content("第一章正文内容用来生成一个场景。").isEmpty(false).build(),
                RawParagraph.builder().index(chapterIndex * 10 + 1).content("这里继续叙述故事情节的发展脉络。").isEmpty(false).build(),
                RawParagraph.builder().index(chapterIndex * 10 + 2).content("至此一段完整的场景就此结束。").isEmpty(false).build()
        );
    }

    /** 记录每次 saveScenesIdempotent 收到的 seq，返回递增 id；其余仓库方法不触发。 */
    private static class RecordingSceneRepository implements SceneRepository {
        private final List<List<Long>> seqCalls = new ArrayList<>();
        private long idSeq = 0L;

        List<List<Long>> seqCalls() {
            return seqCalls;
        }

        List<Long> allSavedSeqs() {
            return seqCalls.stream().flatMap(List::stream).collect(Collectors.toList());
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
        @Override public PagedResult<Scene> findLightweightScenes(PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByNovelId(String novelId, PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByProfile(String novelId, String version, int chunkSize, int chunkOverlap, PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId, PageQuery pageQuery) { throw unsupported(); }
        @Override public PagedResult<Scene> findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, PageQuery pageQuery) { throw unsupported(); }
        @Override public List<SceneCountByProfile> countScenesByNovelVersionAndChunk() { throw unsupported(); }
        @Override public int resetEmbedStateForRun(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId) { throw unsupported(); }
        @Override public void updateEmbedOutcome(Long persistenceId, String embedRunId, EmbedStatus status, String embedError) { throw unsupported(); }
        @Override public void batchUpdateEmbedOutcome(List<Long> persistenceIds, String embedRunId, EmbedStatus status, String embedError) { throw unsupported(); }
        @Override public long countEmbedByRunAndStatus(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId, EmbedStatus status) { throw unsupported(); }
        @Override public List<Long> listPersistenceIdsForEmbedResume(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId) { throw unsupported(); }
        @Override public Optional<int[]> resolveChunkProfileForEmbedRun(String novelId, String version, String embedRunId) { throw unsupported(); }
        @Override public List<Scene> findByProfileAndSeqRange(String novelId, String version, int chunkSize, int chunkOverlap, long fromSeq, long toSeq) { throw unsupported(); }
        @Override public void updateScenesMetadata(List<Scene> scenes) { throw unsupported(); }
    }

    /** 内存版 NovelVersionRepository：真实保存/读取，供游标续传断言。 */
    static class InMemoryNovelVersionRepository implements NovelVersionRepository {
        final Map<String, NovelVersion> store = new LinkedHashMap<>();

        private static String key(String novelId, String versionTag) {
            return novelId + "\0" + versionTag;
        }

        @Override public void save(NovelVersion version) {
            store.put(key(version.getNovelId(), version.getVersionTag()), version);
        }

        @Override public Optional<NovelVersion> findById(String novelId, String versionTag) {
            return Optional.ofNullable(store.get(key(novelId, versionTag)));
        }

        @Override public List<NovelVersion> findByNovelId(String novelId) {
            return store.values().stream().filter(v -> novelId.equals(v.getNovelId())).collect(Collectors.toList());
        }

        @Override public void delete(String novelId, String versionTag) {
            store.remove(key(novelId, versionTag));
        }

        @Override public void deleteByNovelId(String novelId) {
            store.keySet().removeIf(k -> k.startsWith(novelId + "\0"));
        }

        @Override public List<NovelVersion> findStalled(List<VersionStatus> statuses, long beforeUpdatedAt) {
            return List.of();
        }
    }
}
