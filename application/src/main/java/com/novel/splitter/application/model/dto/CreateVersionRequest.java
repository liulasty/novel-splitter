package com.novel.splitter.application.model.dto;

import lombok.Data;

/**
 * 创建小说版本（NovelVersion）请求。
 * <p>
 * versionTag 为空时自动生成 {@code v{n+1}}；chunkSize/chunkOverlap 为空时取应用默认（350/65）。
 * </p>
 */
@Data
public class CreateVersionRequest {

    /** 版本标签；为空时按该小说已存在版本自动递增（v1 → v2 → …） */
    private String versionTag;

    /** 切分策略：SCENE_BOUNDARY / OVERLAP_CHUNK / SEMANTIC；非法值返回 400 */
    private String splitStrategy;

    /** 场景滑窗块大小（字/字符数） */
    private Integer chunkSize;

    /** 相邻块重叠字数 */
    private Integer chunkOverlap;
}
