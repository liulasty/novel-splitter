package com.novel.splitter.application.service.knowledge;

import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.VectorPreviewRecordDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 知识库管理服务接口
 */
public interface KnowledgeBaseService {
    
    /**
     * 获取轻量级场景分页列表
     * @param pageable 分页参数
     * @return 轻量级场景分页列表
     */
    Page<VectorPreviewRecordDto> getLightweightScenes(Pageable pageable);

    /**
     * 获取指定小说的所有 Scene
     * @param novelName 小说名称
     * @return Scene 列表
     */
    List<SceneDto> getScenesByNovel(String novelName);

    /**
     * 删除指定版本的切分结果（及相关向量）
     * @param novelName 小说名称
     * @param version 版本
     * @return cleanupTaskId
     */
    Long deleteVersion(String novelName, String version);

    /**
     * 删除指定小说的所有数据（文件、切分结果、向量）
     * @param novelName 小说名称
     * @return cleanupTaskId
     */
    Long deleteKnowledgeBase(String novelName);

    /**
     * 按 novelId 删除指定小说的所有数据（文件、切分结果、向量）
     * @param novelId novels 表主键
     * @return cleanupTaskId
     */
    Long deleteKnowledgeBaseById(String novelId);

    /**
     * 获取指定小说的所有版本列表
     * @param novelName 小说名称
     * @return 版本列表
     */
    List<String> listVersions(String novelName);
}
