package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 系统配置响应 DTO — 按 category 分组，每组包含多个 ConfigItem。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingsDto {
    /** category → items */
    private Map<String, List<ConfigItem>> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigItem {
        private Long id;
        private String configKey;
        private String configValue;
        private String category;
        private String description;
        /** true = 来自 yml 默认值，未被 DB 覆盖 */
        private boolean isDefault;
    }
}
