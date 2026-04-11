package com.novel.splitter.domain.model;

/**
 * JPQL 构造函数投影：按 (novelId, version, chunkSize, chunkOverlap) 分组的场景数量统计。
 */
public record SceneCountByProfile(
        String novelId,
        String version,
        Integer chunkSize,
        Integer chunkOverlap,
        Long sceneCount
) {}
