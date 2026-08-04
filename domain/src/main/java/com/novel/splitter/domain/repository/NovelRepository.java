package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.Novel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 小说源文件及元数据仓库接口
 */
public interface NovelRepository {
    /**
     * 硬删除小说记录（物理删除行；用于入库原子回滚等"无残留"场景，区别于软删）。
     */
    void hardDelete(String id);

    /**
     * 保存小说实体
     */
    void save(Novel novel);

    /**
     * 根据ID查找小说
     */
    Optional<Novel> findById(String id);

    /**
     * 根据 ID 查找并加悲观写锁（用于与任务创建/删除等同事务串行化，避免并发下的检查-提交竞态）。
     */
    Optional<Novel> findByIdForUpdate(String id);

    /**
     * 根据标题查找小说（用于兼容 legacy novelName 入口）
     */
    Optional<Novel> findByTitle(String title);

    /**
     * 列出所有小说
     */
    List<Novel> findAll();

    /**
     * 加载原始小说内容
     * @param path 文件路径
     * @return 文本行列表
     * @throws IOException IO异常
     */
    List<String> loadRaw(Path path) throws IOException;
}
