package com.novel.splitter.application.service.settings;

import com.novel.splitter.application.model.dto.SystemSettingsDto;

public interface SystemSettingsService {
    
    /**
     * Get system settings from local JSON file.
     * @return SystemSettingsDto
     */
    SystemSettingsDto getSettings();
    
    /**
     * Save system settings to local JSON file.
     * @param settingsDto The settings to save
     */
    void saveSettings(SystemSettingsDto settingsDto);
}
