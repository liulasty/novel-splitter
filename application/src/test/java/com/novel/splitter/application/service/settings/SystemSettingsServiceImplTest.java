package com.novel.splitter.application.service.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SystemSettingsServiceImplTest {

    private SystemSettingsServiceImpl systemSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TEST_SETTINGS_FILE = "config/settings.json";

    @BeforeEach
    void setUp() throws Exception {
        systemSettingsService = new SystemSettingsServiceImpl(objectMapper);
        // Clean up before test
        File file = new File(TEST_SETTINGS_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up after test
        File file = new File(TEST_SETTINGS_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    void testInitCreatesDefaultFile() {
        systemSettingsService.init();
        File file = new File(TEST_SETTINGS_FILE);
        assertTrue(file.exists());
    }

    @Test
    void testGetSettingsReturnsEmptyWhenFileNotExists() {
        SystemSettingsDto settings = systemSettingsService.getSettings();
        assertNotNull(settings);
        assertNull(settings.getEmbedding());
    }

    @Test
    void testSaveAndGetSettings() {
        SystemSettingsDto dto = new SystemSettingsDto();
        dto.setEmbedding(Map.of("type", "chroma"));
        dto.setLlm(Map.of("provider", "ollama"));
        
        systemSettingsService.saveSettings(dto);
        
        File file = new File(TEST_SETTINGS_FILE);
        assertTrue(file.exists());
        
        SystemSettingsDto loaded = systemSettingsService.getSettings();
        assertNotNull(loaded);
        assertEquals("chroma", loaded.getEmbedding().get("type"));
        assertEquals("ollama", loaded.getLlm().get("provider"));
    }
}
