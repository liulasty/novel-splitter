package com.novel.splitter.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 场景切分（Split 队列）：要求小说已完成章节结构化。
 * <p>
 * 落库时按 (novelId, version) 分区：任务开始前会删除该小说下<strong>同名 version</strong> 的旧场景与向量，再写入本次切分产生的<strong>多条</strong> Scene。
 * chunk 规则（chunkSize/chunkOverlap）<strong>不会</strong>自动写入 version；若要用不同滑窗规则并存多套切片，请显式使用不同的 {@code version} 字符串（例如 {@code v1-c350-o64}、{@code v2-c512-o96}）。
 */
@Data
public class SceneSplitRequestDto {
    private int maxScenes = 0;
    @Schema(description = "场景数据集版本标签。同一小说下不同 chunk 规则若要并存，必须使用不同 version；相同 version 会覆盖该版本既有场景与向量。")
    private String version;
    @Schema(description = "滑窗块大小（字符数），可选；覆盖全局默认")
    private Integer chunkSize;
    @Schema(description = "块重叠（字符数），可选，须小于 chunkSize")
    private Integer chunkOverlap;
    /** true：场景切分成功后自动投递向量化任务 */
    private boolean triggerEmbed = false;
}
