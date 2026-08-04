package com.novel.splitter.application.worker;

import com.novel.splitter.application.port.out.FileStoragePort;
import com.novel.splitter.application.service.novel.ChapterService;
import com.novel.splitter.application.service.novel.ChapterServiceImpl;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.ingest.IngestRollbackService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.pipeline.orchestrator.LoadNovelUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段一原子基准契约测试（Mockito 装配，直接驱动真实生产路径 {@link LoadWorker#processLoadTask}）。
 *
 * <p>application 模块现有 worker 测试均为纯 Mockito 单测（无 Spring 上下文）；本模块 application.yml 依赖
 * PostgreSQL / RabbitMQ / Chroma，无法轻量起完整 {@code @SpringBootTest}。因此用最小装配驱动 worker，
 * 把「原子 / 回滚」语义断言落在被抽出的核心方法上：
 * <ul>
 *   <li>解析失败 → novel 回到 PENDING（或解析前状态）、绝不触发 chapters 落库（无半成品）、任务行记 FAILED；</li>
 *   <li>force 重解析成功 → 经 {@code replaceAll} 一次性整体替换新基准（无旧残留、解析前不再预删 chapters）；</li>
 *   <li>{@code replaceAll} 清旧 + 写新在同一事务调用内完成（见 {@link ChapterServiceImpl#replaceAll}）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LoadWorkerAtomicTest {

    @Mock
    private LoadNovelUseCase loadNovelUseCase;
    @Mock
    private TaskService taskService;
    @Mock
    private NovelCacheRepository novelCacheRepository;
    @Mock
    private FileStoragePort fileStoragePort;
    @Mock
    private NovelService novelService;
    @Mock
    private ChapterService chapterService;
    @Mock
    private IngestRollbackService ingestRollbackService;

    @InjectMocks
    private LoadWorker loadWorker;

    @TempDir
    Path tmp;

    private Path upload;
    private Path rawOriginal;

    @BeforeEach
    void setUp() throws Exception {
        upload = tmp.resolve("demo.txt");
        Files.writeString(upload, "第一章 开始\n正文内容...\n第二章 继续\n正文内容...\n");
        rawOriginal = tmp.resolve("n1").resolve("original.txt");
    }

    private SplitTask chapterParseTask() {
        return new SplitTask("t1", TaskType.CHAPTER_PARSE, "n1", "demo.txt", 0, "v1");
    }

    private void stubRawCopyInputs() throws Exception {
        when(fileStoragePort.toAbsolutePath("demo.txt")).thenReturn(upload);
        when(novelCacheRepository.rawOriginalPath("n1")).thenReturn(rawOriginal);
    }

    /**
     * 契约：解析必然失败（章节识别抛异常）时——novel 状态回到 PENDING（而非 FAILED），
     * 且不产生任何半成品章节（绝不触发 chapters 落库），任务行记录 FAILED 用于人工排查。
     */
    @Test
    void parseFailureLeavesNoChaptersAndNovelBackToPending() throws Exception {
        when(taskService.getTask("t1")).thenReturn(chapterParseTask());
        when(chapterService.hasChapters("n1")).thenReturn(false);
        // parsed 目录不存在 → hasParsedFiles=false，避免额外 listChapterFiles 桩
        when(novelCacheRepository.parsedDirPath("n1")).thenReturn(tmp.resolve("n1-parsed-missing"));
        stubRawCopyInputs();
        when(novelService.getNovelById("n1"))
                .thenReturn(Novel.builder().id("n1").status(NovelStatus.PENDING).build());
        when(loadNovelUseCase.load(eq("n1"), any(Path.class), any(), isNull(), isNull()))
                .thenThrow(new RuntimeException("章节识别失败"));

        loadWorker.processLoadTask(new SplitTaskMessage("t1", "n1", 0, "v1"));

        // 进入了解析流程
        verify(novelService).updateNovelStatus("n1", NovelStatus.SPLITTING);
        // 失败后状态回滚到 PENDING，而不是 FAILED
        verify(novelService).updateNovelStatus("n1", NovelStatus.PENDING);
        verify(novelService, never()).updateNovelStatus("n1", NovelStatus.FAILED);
        verify(novelService, never()).updateNovelStatus("n1", NovelStatus.PARSED);
        // 无半成品：绝不落库章节
        verify(chapterService, never()).replaceAll(anyString(), anyList());
        verify(chapterService, never()).saveChapters(anyList());
        // 任务行仍记录 FAILED（人工排查）
        verify(taskService).updateTaskStatus(eq("t1"), eq(SplitTask.TaskStatus.FAILED), eq(0), anyString());
        // 未带回滚标记（重解析等场景）：不触发整体回滚
        verify(ingestRollbackService, never()).rollback(anyString());
    }

    /**
     * 契约：入库任务（rollbackOnFailure=true）解析失败 → 调用 IngestRollbackService 整体回滚，
     * 任务行记录 FAILED 用于人工排查。
     */
    @Test
    void ingestTaskFailure_rollsBackNovelWhenFlagSet() throws Exception {
        when(taskService.getTask("t1")).thenReturn(chapterParseTask());
        when(chapterService.hasChapters("n1")).thenReturn(false);
        when(novelCacheRepository.parsedDirPath("n1")).thenReturn(tmp.resolve("n1-parsed-missing"));
        stubRawCopyInputs();
        when(novelService.getNovelById("n1"))
                .thenReturn(Novel.builder().id("n1").status(NovelStatus.PENDING).build());
        when(loadNovelUseCase.load(eq("n1"), any(Path.class), any(), isNull(), isNull()))
                .thenThrow(new RuntimeException("章节识别失败"));

        SplitTaskMessage msg = new SplitTaskMessage("t1", "n1", 0, "v1");
        msg.setRollbackOnFailure(true);
        loadWorker.processLoadTask(msg);

        verify(ingestRollbackService).rollback("n1");
        verify(taskService).updateTaskStatus(eq("t1"), eq(SplitTask.TaskStatus.FAILED), eq(0), anyString());
    }

    /**
     * 契约：已有完整基准 + force 重解析成功 → chapters 整体替换为新完整集合（无旧残留），
     * 解析前不再预删 DB chapters（整体替换交给同事务的 {@code replaceAll}）。
     */
    @Test
    void reparseReplacesBaselineAtomically() throws Exception {
        when(taskService.getTask("t1")).thenReturn(chapterParseTask());
        when(chapterService.hasChapters("n1")).thenReturn(true);

        Path parsedDir = tmp.resolve("n1-parsed");
        Files.createDirectories(parsedDir);
        Files.writeString(parsedDir.resolve("chapter_1.json"), "{}");
        when(novelCacheRepository.parsedDirPath("n1")).thenReturn(parsedDir);
        when(novelCacheRepository.listChapterFiles("n1")).thenReturn(Files.list(parsedDir));

        stubRawCopyInputs();
        when(novelService.getNovelById("n1"))
                .thenReturn(Novel.builder().id("n1").status(NovelStatus.PARSED).build());

        Novel parsed = Novel.builder()
                .chapters(List.of(
                        Chapter.builder().index(1).title("新第一章").startParagraphIndex(1).endParagraphIndex(5).wordCount(20).build(),
                        Chapter.builder().index(2).title("新第二章").startParagraphIndex(6).endParagraphIndex(9).wordCount(15).build()))
                .build();
        when(loadNovelUseCase.load(eq("n1"), any(Path.class), any(), isNull(), isNull())).thenReturn(parsed);

        SplitTaskMessage msg = new SplitTaskMessage("t1", "n1", 0, "v1");
        msg.setForceReload(true);
        loadWorker.processLoadTask(msg);

        // 清理旧 parsed 文件缓存（可重建），但不再解析前预删 DB chapters
        verify(novelCacheRepository).removeParsedArtifacts("n1");
        verify(chapterService, never()).deleteByNovelId("n1");

        // 整体替换：一次性传入完整新集合
        ArgumentCaptor<List<Chapter>> captor = ArgumentCaptor.forClass(List.class);
        verify(chapterService).replaceAll(eq("n1"), captor.capture());
        List<Chapter> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Chapter::getTitle).containsExactly("新第一章", "新第二章");
        assertThat(saved).allSatisfy(c -> assertThat(c.getNovelId()).isEqualTo("n1"));

        verify(novelService).updateNovelStatus("n1", NovelStatus.PARSED);
        verify(taskService).updateTaskStatus(eq("t1"), eq(SplitTask.TaskStatus.SUCCESS), eq(100), anyString());
    }

    /**
     * 幂等短路不受破坏：完整产物 + 非 force 时跳过解析，不触发任何落库/清理。
     */
    @Test
    void completeArtifactsWithoutForce_skipsParseIdempotently() throws Exception {
        when(taskService.getTask("t1")).thenReturn(chapterParseTask());
        when(chapterService.hasChapters("n1")).thenReturn(true);

        Path parsedDir = tmp.resolve("n1-parsed");
        Files.createDirectories(parsedDir);
        Files.writeString(parsedDir.resolve("chapter_1.json"), "{}");
        when(novelCacheRepository.parsedDirPath("n1")).thenReturn(parsedDir);
        when(novelCacheRepository.listChapterFiles("n1")).thenReturn(Files.list(parsedDir));

        loadWorker.processLoadTask(new SplitTaskMessage("t1", "n1", 0, "v1"));

        verify(chapterService, never()).saveChapters(anyList());
        verify(chapterService, never()).replaceAll(anyString(), anyList());
        verify(chapterService, never()).deleteByNovelId("n1");
        verify(novelService).updateNovelStatus("n1", NovelStatus.PARSED);
        verify(taskService).updateTaskStatus(eq("t1"), eq(SplitTask.TaskStatus.SUCCESS), eq(100), anyString());
    }

    /** replaceAll 清旧 + 写新必须在单次调用内顺序完成（同事务保证）。 */
    @Test
    void replaceAllClearsOldThenInsertsNewInOneCall() {
        ChapterRepository repo = mock(ChapterRepository.class);
        ChapterServiceImpl svc = new ChapterServiceImpl(repo);

        Chapter c1 = Chapter.builder().novelId("n1").index(1).title("新章一").build();
        svc.replaceAll("n1", List.of(c1));

        InOrder inOrder = inOrder(repo);
        inOrder.verify(repo).deleteByNovelId("n1");
        inOrder.verify(repo).saveAll(List.of(c1));
    }

    /** 空新集合同样整体替换：清空旧基准，不产生残留。 */
    @Test
    void replaceAllWithEmptyChaptersStillClearsOldBaseline() {
        ChapterRepository repo = mock(ChapterRepository.class);
        ChapterServiceImpl svc = new ChapterServiceImpl(repo);

        svc.replaceAll("n1", List.of());

        verify(repo).deleteByNovelId("n1");
        verify(repo, never()).saveAll(anyList());
    }
}
