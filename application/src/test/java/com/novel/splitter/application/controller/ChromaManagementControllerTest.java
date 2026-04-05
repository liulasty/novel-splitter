package com.novel.splitter.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.common.AuthInterceptor;
import com.novel.splitter.embedding.admin.ChromaAdminService;
import com.novel.splitter.application.service.chroma.ChromaHttpProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChromaManagementControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChromaAdminService chromaAdminService;

    @Mock
    private ChromaHttpProxy chromaHttpProxy;

    @Mock
    private AuthInterceptor authInterceptor;

    @InjectMocks
    private ChromaManagementController chromaManagementController;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(chromaManagementController)
                .addInterceptors(authInterceptor)
                .build();
    }

    @Test
    void shouldReturnStatsFromService() throws Exception {
        when(chromaAdminService.getStats()).thenReturn(ResponseEntity.ok(Map.of("count", 3, "storeType", "MockStore")));

        mockMvc.perform(get("/api/admin/chroma/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.storeType").value("MockStore"));

        verify(chromaAdminService).getStats();
    }

    @Test
    void shouldDelegateTenantCreationToService() throws Exception {
        ResponseEntity<?> response = ResponseEntity.ok(Map.of("message", "ok"));
        doReturn(response).when(chromaHttpProxy).post(anyString(), any());

        mockMvc.perform(post("/api/admin/chroma/tenants")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "tenant-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ok"));

        verify(chromaHttpProxy).post(anyString(), any());
    }
}
