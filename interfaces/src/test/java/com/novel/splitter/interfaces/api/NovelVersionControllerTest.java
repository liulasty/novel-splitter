package com.novel.splitter.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.CreateVersionRequest;
import com.novel.splitter.application.model.dto.NovelVersionDto;
import com.novel.splitter.application.model.dto.ReparseChaptersRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.service.novel.NovelFacadeService;
import com.novel.splitter.interfaces.common.GlobalExceptionHandler;
import com.novel.splitter.interfaces.common.GlobalResponseAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 版本化流水线 REST 端点契约测试（Task 20）：路由存在性 + 委托 facade + 返回包装格式。
 */
@ExtendWith(MockitoExtension.class)
class NovelVersionControllerTest {

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

    private static NovelVersionDto dto(String versionTag) {
        return NovelVersionDto.builder()
                .novelId("n1")
                .versionTag(versionTag)
                .splitStrategy("OVERLAP_CHUNK")
                .chunkSize(512)
                .chunkOverlap(64)
                .status("PENDING")
                .active(true)
                .build();
    }

    @Test
    void listVersions_returns200_andDelegatesToFacade() throws Exception {
        when(novelFacadeService.listVersions("n1")).thenReturn(List.of(dto("v1")));

        mockMvc.perform(get("/api/novels/n1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].versionTag").value("v1"));

        verify(novelFacadeService).listVersions("n1");
    }

    @Test
    void createVersion_returns200_andDelegatesToFacade() throws Exception {
        when(novelFacadeService.createVersion(eq("n1"), any(CreateVersionRequest.class))).thenReturn(dto("v2"));

        CreateVersionRequest req = new CreateVersionRequest();
        req.setVersionTag("v2");
        req.setSplitStrategy("OVERLAP_CHUNK");
        req.setChunkSize(512);
        req.setChunkOverlap(64);

        mockMvc.perform(post("/api/novels/n1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.versionTag").value("v2"));

        verify(novelFacadeService).createVersion(eq("n1"), any(CreateVersionRequest.class));
    }

    @Test
    void baselineParse_returns200_andDelegatesToFacade() throws Exception {
        when(novelFacadeService.baselineParse(eq("n1"), any(ReparseChaptersRequestDto.class)))
                .thenReturn(TaskSubmitResponseDto.builder().taskId("t1").message("解析已提交").build());

        mockMvc.perform(post("/api/novels/n1/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("t1"));

        verify(novelFacadeService).baselineParse(eq("n1"), any(ReparseChaptersRequestDto.class));
    }

    @Test
    void startVersionSplit_returns200_andDelegatesToFacade() throws Exception {
        when(novelFacadeService.startVersionSplit("n1", "v1"))
                .thenReturn(TaskSubmitResponseDto.builder().taskId("t-split").build());

        mockMvc.perform(post("/api/novels/n1/versions/v1/split"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("t-split"));

        verify(novelFacadeService).startVersionSplit("n1", "v1");
    }

    @Test
    void startVersionEmbed_returns200_andDelegatesToFacade() throws Exception {
        when(novelFacadeService.startVersionEmbed("n1", "v1"))
                .thenReturn(TaskSubmitResponseDto.builder().taskId("t-embed").build());

        mockMvc.perform(post("/api/novels/n1/versions/v1/embed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("t-embed"));

        verify(novelFacadeService).startVersionEmbed("n1", "v1");
    }

    @Test
    void activateVersion_returns200_andDelegatesToFacade() throws Exception {
        mockMvc.perform(post("/api/novels/n1/versions/v1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(novelFacadeService).activateVersion("n1", "v1");
    }

    @Test
    void deleteVersion_returns200_andDelegatesToFacade() throws Exception {
        mockMvc.perform(delete("/api/novels/n1/versions/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(novelFacadeService).deleteVersion("n1", "v1");
    }
}
