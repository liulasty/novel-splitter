package com.novel.splitter.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.ConfigSaveRequest;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import com.novel.splitter.application.service.settings.SystemSettingsService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingsControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SystemSettingsService systemSettingsService;

    @InjectMocks
    private SystemSettingsController controller;

    @BeforeEach
    void setUp() {
        GlobalResponseAdvice advice = new GlobalResponseAdvice();
        ReflectionTestUtils.setField(advice, "objectMapper", objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), advice)
                .build();
    }

    @Test
    void testGetSettings() throws Exception {
        SystemSettingsDto.ConfigItem item = SystemSettingsDto.ConfigItem.builder()
                .id(1L).configKey("test").configValue("v").category("other").isDefault(true).build();
        SystemSettingsDto dto = SystemSettingsDto.builder()
                .categories(Map.of("other", List.of(item)))
                .build();
        when(systemSettingsService.getSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.categories.other[0].configKey").value("test"));
    }

    @Test
    void testSave() throws Exception {
        ConfigSaveRequest req = new ConfigSaveRequest();
        req.setConfigKey("test.key");
        req.setConfigValue("123");
        req.setCategory("splitter");

        mockMvc.perform(post("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(systemSettingsService).save(any(ConfigSaveRequest.class));
    }
}
