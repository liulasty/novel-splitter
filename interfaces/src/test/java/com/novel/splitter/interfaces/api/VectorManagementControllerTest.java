package com.novel.splitter.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.VectorRecordDto;
import com.novel.splitter.application.model.dto.VectorSearchRequest;
import com.novel.splitter.application.service.vector.VectorManagementService;
import com.novel.splitter.domain.model.embedding.VectorRecord;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VectorManagementControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private VectorManagementService vectorManagementService;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private VectorManagementController vectorManagementController;

    @BeforeEach
    void setUp() {
        GlobalResponseAdvice globalResponseAdvice = new GlobalResponseAdvice();
        ReflectionTestUtils.setField(globalResponseAdvice, "objectMapper", objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(vectorManagementController)
                .setControllerAdvice(new GlobalExceptionHandler(), globalResponseAdvice)
                .build();
    }

    @Test
    void shouldReturnStatsFromService() throws Exception {
        when(vectorManagementService.getStats()).thenReturn(Map.of("count", 2, "type", "MockStore"));

        mockMvc.perform(get("/api/admin/vector/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.type").value("MockStore"));

        verify(vectorManagementService).getStats();
    }

    @Test
    void shouldDelegateSearchToService() throws Exception {
        when(vectorManagementService.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorRecord("1", 0.9, Map.of())));
        when(dtoMapper.toVectorRecordDtos(any()))
                .thenReturn(List.of(VectorRecordDto.builder().id("1").score(0.9).build()));

        mockMvc.perform(post("/api/admin/vector/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "测试", "topK", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].score").value(0.9));

        verify(vectorManagementService).search(any(VectorSearchRequest.class));
        verify(dtoMapper).toVectorRecordDtos(any());
    }

    @Test
    void shouldDelegateDeleteToService() throws Exception {
        mockMvc.perform(delete("/api/admin/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("novelId", "demo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(vectorManagementService).delete(any());
    }
}
