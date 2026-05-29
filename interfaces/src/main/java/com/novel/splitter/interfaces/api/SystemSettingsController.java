package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.ConfigSaveRequest;
import com.novel.splitter.application.model.dto.MessageResponseDto;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import com.novel.splitter.application.model.dto.SystemSettingsDto.ConfigItem;
import com.novel.splitter.application.service.settings.SystemSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统设置管理", description = "系统配置的获取、新增、修改、删除")
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService service;

    @Operation(summary = "获取全量配置", description = "按 category 分组返回所有配置项；DB 有值用 DB，否则展示 yml 默认值")
    @GetMapping
    public SystemSettingsDto getSettings() {
        return service.getSettings();
    }

    @Operation(summary = "保存单条配置", description = "新增或更新一条配置；configKey 已存在则覆盖")
    @PostMapping
    public ConfigItem save(@RequestBody ConfigSaveRequest request) {
        return service.save(request);
    }

    @Operation(summary = "删除单条配置", description = "按 ID 删除 DB 中的配置覆盖，退回 yml 默认值")
    @DeleteMapping("/{id:\\d+}")
    public MessageResponseDto deleteById(@PathVariable("id") Long id) {
        service.delete(id);
        return MessageResponseDto.builder().message("deleted").build();
    }

    @Operation(summary = "按 key 删除配置", description = "根据 configKey 删除覆盖项")
    @DeleteMapping("/key")
    public MessageResponseDto deleteByKey(@RequestParam("key") String configKey) {
        service.deleteByKey(configKey);
        return MessageResponseDto.builder().message("deleted").build();
    }
}
