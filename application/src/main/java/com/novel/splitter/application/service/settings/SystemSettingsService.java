package com.novel.splitter.application.service.settings;

import com.novel.splitter.application.model.dto.ConfigSaveRequest;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import com.novel.splitter.application.model.dto.SystemSettingsDto.ConfigItem;

import java.util.Map;

public interface SystemSettingsService {

    SystemSettingsDto getSettings();

    ConfigItem save(ConfigSaveRequest request);

    void delete(Long id);

    void deleteByKey(String configKey);

    Map<String, ConfigItem> getYmlDefaults();
}
