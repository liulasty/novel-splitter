package com.novel.splitter.domain.model;

/**
 * 场景数据集在库中的唯一分区：业务版本 + 生效的滑窗参数。
 */
public record SceneSplitProfile(String version, Integer chunkSize, Integer chunkOverlap) {
}
