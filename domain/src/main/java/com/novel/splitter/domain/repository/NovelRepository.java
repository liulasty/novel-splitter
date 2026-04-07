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
     * 保存小说实体
     */
    void save(Novel novel);

    /**
     * 根据ID查找小说
     */
    Optional<Novel> findById(String id);

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
