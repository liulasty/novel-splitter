package com.novel.splitter.application.model.dto;

import lombok.Data;

@Data
public class ConfigSaveRequest {
    private String configKey;
    private String configValue;
    private String category;
    private String description;
}
