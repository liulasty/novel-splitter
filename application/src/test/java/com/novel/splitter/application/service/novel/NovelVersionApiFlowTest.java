package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.CreateVersionRequest;
import com.novel.splitter.application.model.dto.NovelVersionDto;
import com.novel.splitter.application.model.dto.ReparseChaptersRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 版本化流水线门面契约测试（Task 19）：createVersion 递增 tag、listVersions 标 active、
 * startVersionSplit/startVersionEmbed 用版本自身参数投队列、非法 splitStrategy 抛 400。
 */
@ExtendWith(MockitoExtension.class)
class NovelVersionApiFlowTest {

    private static final String NOVEL_ID = "n1";
    private static final String VERSION_TAG = "v1";

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(novelFacadeService, "defaultChunkSize", 350);
        ReflectionTestUtils.setField(novelFacadeService, "defaultChunkOverlap", 65);
    }

    private static NovelVersion version(String tag, VersionStatus status, Integer chunkSize, Integer chunkOverlap) {
        return NovelVersion.builder()
                .novelId(NOVEL_ID)
                .versionTag(tag)
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .status(status)
                .createdAt(1000L)
                .updatedAt(1000L)
                .build();
    }

    @Test
    void createVersion_autoIncrementsTag_whenVersionTagBlank() {
        when(novelService.getNovelById(NOVEL_ID)).thenReturn(Novel.builder().id(NOVEL_ID).activeVersionTag("v1").build());
        when(novelVersionRepository.findByNovelId(NOVEL_ID))
                .thenReturn(List.of(version("v1", VersionStatus.ACTIVE, 512, 64)));

        CreateVersionRequest req = new CreateVersionRequest();
        req.setSplitStrategy("OVERLAP_CHUNK");
        req.setChunkSize(512);
        req.setChunkOverlap(64);

        NovelVersionDto dto = novelFacadeService.createVersion(NOVEL_ID, req);

        assertThat(dto.getVersionTag()).isEqualTo("v2");
        assertThat(dto.getStatus()).isEqualTo(VersionStatus.PENDING.name());
        verify(novelVersionRepository).save(argThat(v -> "v2".equals(v.getVersionTag())
                && v.getStatus() == VersionStatus.PENDING
                && v.getChunkSize() == 512
                && v.getChunkOverlap() == 64));
    }

    @Test
    void createVersion_usesDefaultChunkParams_whenNotProvided() {
        when(novelService.getNovelById(NOVEL_ID)).thenReturn(Novel.builder().id(NOVEL_ID).build());

        CreateVersionRequest req = new CreateVersionRequest();
        req.setVersionTag("v1");
        req.setSplitStrategy("OVERLAP_CHUNK");

        NovelVersionDto dto = novelFacadeService.createVersion(NOVEL_ID, req);

        assertThat(dto.getChunkSize()).isEqualTo(350);
        assertThat(dto.getChunkOverlap()).isEqualTo(65);
    }

    @Test
    void listVersions_marksActiveByNovelActiveVersionTag() {
        when(novelService.getNovelById(NOVEL_ID))
                .thenReturn(Novel.builder().id(NOVEL_ID).activeVersionTag("v1").build());
        when(novelVersionRepository.findByNovelId(NOVEL_ID)).thenReturn(List.of(
                version("v1", VersionStatus.ACTIVE, 512, 64),
                version("v2", VersionStatus.EMBED_DONE, 512, 64)));

        List<NovelVersionDto> dtos = novelFacadeService.listVersions(NOVEL_ID);

        assertThat(dtos).hasSize(2);
        assertThat(dtos.stream().filter(d -> d.getVersionTag().equals("v1")).findFirst())
                .isPresent()
                .get()
                .extracting(NovelVersionDto::isActive)
                .isEqualTo(true);
        assertThat(dtos.stream().filter(d -> d.getVersionTag().equals("v2")).findFirst())
                .isPresent()
                .get()
                .extracting(NovelVersionDto::isActive)
                .isEqualTo(false);
    }

    @Test
    void startVersionSplit_usesVersionChunkParams_whenPushingSplitQueue() throws Exception {
        when(novelService.getNovelById(NOVEL_ID))
                .thenReturn(Novel.builder().id(NOVEL_ID).status(NovelStatus.SPLIT_COMPLETED).build());
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG))
                .thenReturn(Optional.of(version(VERSION_TAG, VersionStatus.PENDING, 512, 64)));

        TaskSubmitResponseDto dto = novelFacadeService.startVersionSplit(NOVEL_ID, VERSION_TAG);

        assertThat(dto.getTaskId()).isNotBlank();
        verify(taskQueuePort).sendSplit(argThat(msg -> VERSION_TAG.equals(msg.getVersion())
                && !msg.isTriggerEmbed()
                && msg.getChunkSize() == 512
                && msg.getChunkOverlap() == 64));
    }

    @Test
    void startVersionSplit_throwsNotFound_whenVersionMissing() {
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> novelFacadeService.startVersionSplit(NOVEL_ID, VERSION_TAG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void startVersionEmbed_delegatesToEmbedOrchestration_withVersionChunkParams() throws Exception {
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG))
                .thenReturn(Optional.of(version(VERSION_TAG, VersionStatus.SPLIT_DONE, 512, 64)));

        TaskSubmitResponseDto dto = novelFacadeService.startVersionEmbed(NOVEL_ID, VERSION_TAG);

        assertThat(dto.getTaskId()).isNotBlank();
        verify(embedPipelineOrchestrator).startNewEmbedRun(any(), eq(NOVEL_ID), eq(VERSION_TAG), eq(512), eq(64));
    }

    @Test
    void createVersion_invalidSplitStrategy_throwsBadRequest() {
        when(novelService.getNovelById(NOVEL_ID)).thenReturn(Novel.builder().id(NOVEL_ID).build());

        CreateVersionRequest req = new CreateVersionRequest();
        req.setVersionTag("v1");
        req.setSplitStrategy("NOT_A_STRATEGY");

        assertThatThrownBy(() -> novelFacadeService.createVersion(NOVEL_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void activateVersion_delegatesToVersionService() {
        novelFacadeService.activateVersion(NOVEL_ID, VERSION_TAG);

        verify(novelVersionService).activate(NOVEL_ID, VERSION_TAG);
    }

    @Test
    void deleteVersion_delegatesToKnowledgeDeletion_andRemovesVersionRow() {
        when(novelVersionRepository.findById(NOVEL_ID, VERSION_TAG))
                .thenReturn(Optional.of(version(VERSION_TAG, VersionStatus.EMBED_DONE, 512, 64)));

        novelFacadeService.deleteVersion(NOVEL_ID, VERSION_TAG);

        verify(knowledgeBaseService).deleteSplitProfileByNovelId(NOVEL_ID, VERSION_TAG, 512, 64, false);
        verify(novelVersionRepository).delete(NOVEL_ID, VERSION_TAG);
    }

    @Test
    void baselineParse_delegatesToLoadQueue_withForceReload() throws Exception {
        when(novelService.getNovelById(NOVEL_ID)).thenReturn(Novel.builder().id(NOVEL_ID).build());

        ReparseChaptersRequestDto req = new ReparseChaptersRequestDto();
        req.setVersion(VERSION_TAG);

        TaskSubmitResponseDto dto = novelFacadeService.baselineParse(NOVEL_ID, req);

        assertThat(dto.getTaskId()).isNotBlank();
        verify(taskQueuePort).sendLoad(argThat(msg -> VERSION_TAG.equals(msg.getVersion()) && msg.isForceReload()));
    }
}
