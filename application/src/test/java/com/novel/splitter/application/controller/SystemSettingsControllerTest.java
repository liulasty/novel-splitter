package com.novel.splitter.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.service.settings.SystemSettingsService;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class SystemSettingsControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SystemSettingsService systemSettingsService;

    @InjectMocks
    private SystemSettingsController systemSettingsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(systemSettingsController).build();
    }

    @Test
    void testGetSettings() throws Exception {
        SystemSettingsDto dto = new SystemSettingsDto();
        dto.setEmbedding(Map.of("type", "chroma"));
        when(systemSettingsService.getSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding.type").value("chroma"));

        verify(systemSettingsService).getSettings();
    }

    @Test
    void testUpdateSettings() throws Exception {
        SystemSettingsDto dto = new SystemSettingsDto();
        dto.setEmbedding(Map.of("type", "chroma"));

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Settings updated successfully"));

        verify(systemSettingsService).saveSettings(any(SystemSettingsDto.class));
    }
}
