package com.novel.splitter.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.common.AuthInterceptor;
import com.novel.splitter.application.common.GlobalExceptionHandler;
import com.novel.splitter.application.common.GlobalResponseAdvice;
import com.novel.splitter.application.service.novel.NovelFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Mock
    private AuthInterceptor authInterceptor;

    @InjectMocks
    private NovelController novelController;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        GlobalResponseAdvice globalResponseAdvice = new GlobalResponseAdvice();
        ReflectionTestUtils.setField(globalResponseAdvice, "objectMapper", objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(novelController)
                .setControllerAdvice(new GlobalExceptionHandler(), globalResponseAdvice)
                .addInterceptors(authInterceptor)
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
        when(novelFacadeService.uploadNovel(any())).thenReturn(Map.of("message", "文件上传成功: demo_1.txt", "fileName", "demo_1.txt"));

        mockMvc.perform(multipart("/api/novels/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("demo_1.txt"));

        verify(novelFacadeService).uploadNovel(any());
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
}
