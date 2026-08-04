package com.novel.splitter.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.NovelSummaryListScope;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.SceneSplitRequestDto;
import com.novel.splitter.application.service.novel.NovelFacadeService;
import com.novel.splitter.interfaces.common.GlobalExceptionHandler;
import com.novel.splitter.interfaces.common.GlobalResponseAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NovelControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NovelFacadeService novelFacadeService;

    @InjectMocks
    private NovelController novelController;

    @BeforeEach
    void setUp() {
        GlobalResponseAdvice globalResponseAdvice = new GlobalResponseAdvice();
        ReflectionTestUtils.setField(globalResponseAdvice, "objectMapper", objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(novelController)
                .setControllerAdvice(new GlobalExceptionHandler(), globalResponseAdvice)
                .build();
    }

    @Test
    void shouldListNovelsByDelegatingToFacadeService() throws Exception {
        when(novelFacadeService.listNovels()).thenReturn(List.of("a.txt", "b.txt"));

        mockMvc.perform(get("/api/novels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value("a.txt"))
                .andExpect(jsonPath("$.data[1]").value("b.txt"));

        verify(novelFacadeService).listNovels();
    }

    @Test
    void shouldUploadNovelByDelegatingToFacadeService() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, "content".getBytes());
        when(novelFacadeService.uploadNovel(any(UploadNovelCommand.class)))
                .thenReturn(NovelUploadResponseDto.builder()
                        .message("文件上传成功")
                        .novelId("demo_1")
                        .build());

        mockMvc.perform(multipart("/api/novels/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.novelId").value("demo_1"));

        verify(novelFacadeService).uploadNovel(any(UploadNovelCommand.class));
    }

    @Test
    void uploadNovel_forwardsStrategyAndReturnsTaskId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, "content".getBytes());
        when(novelFacadeService.uploadNovel(argThat(cmd ->
                "CN_CHAPTER".equals(cmd.strategy()) && cmd.chapterTitleRegex() == null)))
                .thenReturn(NovelUploadResponseDto.builder()
                        .message("文件上传成功，章节解析任务已提交")
                        .novelId("demo_1")
                        .taskId("task-1")
                        .build());

        mockMvc.perform(multipart("/api/novels/upload")
                        .file(file)
                        .param("strategy", "CN_CHAPTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.novelId").value("demo_1"))
                .andExpect(jsonPath("$.data.taskId").value("task-1"));

        verify(novelFacadeService).uploadNovel(any(UploadNovelCommand.class));
    }

    @Test
    void shouldListNovelSummariesWithDefaultScopeAll() throws Exception {
        when(novelFacadeService.listNovelSummaries(NovelSummaryListScope.ALL)).thenReturn(List.of());

        mockMvc.perform(get("/api/novels/summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(novelFacadeService).listNovelSummaries(NovelSummaryListScope.ALL);
    }

    @Test
    void shouldListNovelSummariesWithEmbedReadyScope() throws Exception {
        when(novelFacadeService.listNovelSummaries(NovelSummaryListScope.EMBED_READY)).thenReturn(List.of());

        mockMvc.perform(get("/api/novels/summaries").param("scope", "embed_ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(novelFacadeService).listNovelSummaries(NovelSummaryListScope.EMBED_READY);
    }

    @Test
    void shouldReturnBadRequestWhenIngestRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/novels/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件名不能为空"));
    }

    @Test
    void sceneSplitReturns409WhenNovelEmbedding() throws Exception {
        when(novelFacadeService.sceneSplit(eq("n1"), any(SceneSplitRequestDto.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "该小说正在向量化（EMBEDDING），为避免与场景数据冲突，请等待向量化完成后再发起场景切分。"));

        mockMvc.perform(post("/api/novels/n1/scene-split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        verify(novelFacadeService).sceneSplit(eq("n1"), any(SceneSplitRequestDto.class));
    }

    @Test
    void getScenesByChapter_passesVersionToFacade() throws Exception {
        when(novelFacadeService.getScenesByChapter("n1", 5L, "v2", 0, 200))
                .thenReturn(com.novel.splitter.domain.model.paging.PagedResult.of(List.of(), 0, 200, 0));

        mockMvc.perform(get("/api/novels/n1/chapters/5/scenes").param("version", "v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(novelFacadeService).getScenesByChapter("n1", 5L, "v2", 0, 200);
    }

    @Test
    void getScenesByChapter_omitsVersion_whenAbsent() throws Exception {
        when(novelFacadeService.getScenesByChapter("n1", 5L, null, 0, 200))
                .thenReturn(com.novel.splitter.domain.model.paging.PagedResult.of(List.of(), 0, 200, 0));

        mockMvc.perform(get("/api/novels/n1/chapters/5/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(novelFacadeService).getScenesByChapter("n1", 5L, null, 0, 200);
    }
}
