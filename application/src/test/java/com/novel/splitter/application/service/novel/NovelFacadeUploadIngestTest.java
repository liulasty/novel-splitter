package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeUploadIngestTest {

    @Mock private NovelStorageService novelStorageService;
    @Mock private NovelService novelService;
    @Mock private ChapterService chapterService;
    @Mock private NovelCacheRepository novelCacheRepository;
    @Mock private TaskService taskService;
    @Mock private TaskQueuePort taskQueuePort;
    @Mock private DownloadService downloadService;
    @Mock private SceneRepository sceneRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private DtoMapper dtoMapper;
    @Mock private EmbedPipelineOrchestrator embedPipelineOrchestrator;
    @Mock private NovelVersionRepository novelVersionRepository;
    @Mock private NovelVersionService novelVersionService;
    @Mock private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    @Test
    void upload_startsAtomicParseTaskWithRollbackFlag() throws Exception {
        ReflectionTestUtils.setField(novelFacadeService, "maxUploadFileSize", DataSize.ofMegabytes(50));
        when(novelService.createNovel(any(java.io.InputStream.class), eq("demo.txt"), any(), any(), any()))
                .thenReturn("n1");
        when(taskService.createTaskWithNovelAdmission(anyString(), eq(TaskType.CHAPTER_PARSE), eq("n1"), eq(0), anyString()))
                .thenReturn(mock(SplitTask.class));

        UploadNovelCommand cmd = new UploadNovelCommand(
                new ByteArrayInputStream("第一章 开始\n正文内容\n".getBytes(StandardCharsets.UTF_8)),
                "demo.txt", null, null, null, 20L, "CN_CHAPTER", null);

        NovelUploadResponseDto resp = novelFacadeService.uploadNovel(cmd);

        ArgumentCaptor<SplitTaskMessage> captor = ArgumentCaptor.forClass(SplitTaskMessage.class);
        verify(taskQueuePort).sendLoad(captor.capture());
        SplitTaskMessage sent = captor.getValue();
        assertThat(resp.getNovelId()).isEqualTo("n1");
        assertThat(resp.getTaskId()).isEqualTo(sent.getTaskId());
        assertThat(sent.getNovelId()).isEqualTo("n1");
        assertThat(sent.getRecognitionStrategy()).isEqualTo("CN_CHAPTER");
        assertThat(sent.isRollbackOnFailure()).isTrue();
    }
}
