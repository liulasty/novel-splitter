package com.novel.splitter.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneSplitProfileDto {
    @Schema(description = "业务版本，如 prod / v1")
    private String version;
    @Schema(description = "场景滑窗块大小（字符）")
    private Integer chunkSize;
    @Schema(description = "块重叠（字符）")
    private Integer chunkOverlap;

    /**
     * 用于下拉展示，例如 {@code v1 (512/64)}；旧数据缺 chunk 时仅返回 version。
     */
    public String getLabel() {
        if (chunkSize == null || chunkOverlap == null) {
            return version != null ? version : "";
        }
        return version + " (" + chunkSize + "/" + chunkOverlap + ")";
    }
}
