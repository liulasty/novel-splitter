package com.novel.splitter.application.service.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.dto.SystemSettingsDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private final ObjectMapper objectMapper;
    private static final String SETTINGS_FILE_PATH = "config/settings.json";

    @PostConstruct
    public void init() {
        File file = new File(SETTINGS_FILE_PATH);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try {
                SystemSettingsDto defaultSettings = new SystemSettingsDto();
                objectMapper.writeValue(file, defaultSettings);
                log.info("Created default settings file at {}", SETTINGS_FILE_PATH);
            } catch (IOException e) {
                log.error("Failed to create default settings file", e);
            }
        }
    }

    @Override
    public SystemSettingsDto getSettings() {
        File file = new File(SETTINGS_FILE_PATH);
        if (!file.exists()) {
            return new SystemSettingsDto();
        }
        try {
            return objectMapper.readValue(file, SystemSettingsDto.class);
        } catch (IOException e) {
            log.error("Failed to read settings file", e);
            throw new RuntimeException("Failed to read settings", e);
        }
    }

    @Override
    public void saveSettings(SystemSettingsDto settingsDto) {
        File file = new File(SETTINGS_FILE_PATH);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, settingsDto);
            log.info("Saved settings to {}", SETTINGS_FILE_PATH);
        } catch (IOException e) {
            log.error("Failed to save settings", e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }
}
