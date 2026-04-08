package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.settings.SystemSettingsService;
import com.novel.splitter.application.model.dto.MessageResponseDto;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing system settings.
 */
@Tag(name = "系统设置管理", description = "提供系统设置的获取和更新功能")
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    @Operation(summary = "获取系统设置", description = "获取当前系统的所有配置信息")
    @GetMapping
    public SystemSettingsDto getSettings() {
        return systemSettingsService.getSettings();
    }

    @Operation(summary = "更新系统设置", description = "更新系统配置并持久化到本地文件")
    @PutMapping
    public MessageResponseDto updateSettings(@RequestBody SystemSettingsDto settingsDto) {
        systemSettingsService.saveSettings(settingsDto);
        return MessageResponseDto.builder()
                .message("Settings updated successfully")
                .build();
    }
}
