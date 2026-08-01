package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.interfaces.common.GlobalExceptionHandler;
import com.novel.splitter.interfaces.common.GlobalResponseAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    private MockMvc mockMvc;
    @Mock private KnowledgeBaseService knowledgeBaseService;
    @InjectMocks private KnowledgeBaseController knowledgeBaseController;

    @BeforeEach
    void setUp() {
        GlobalResponseAdvice advice = new GlobalResponseAdvice();
        ReflectionTestUtils.setField(advice, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(knowledgeBaseController)
                .setControllerAdvice(new GlobalExceptionHandler(), advice)
                .build();
    }

    @Test
    void getScenesByNovelId_passesVersion() throws Exception {
        when(knowledgeBaseService.getScenesByNovelId("n1", "v2")).thenReturn(List.of());

        mockMvc.perform(get("/api/knowledge/id/n1/scenes").param("version", "v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(knowledgeBaseService).getScenesByNovelId("n1", "v2");
    }

    @Test
    void getScenesByNovelId_omitsVersion_whenAbsent() throws Exception {
        when(knowledgeBaseService.getScenesByNovelId("n1", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/knowledge/id/n1/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(knowledgeBaseService).getScenesByNovelId("n1", null);
    }
}
