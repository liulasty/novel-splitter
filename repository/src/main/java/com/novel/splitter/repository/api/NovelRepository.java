package com.novel.splitter.repository.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 小说源文件仓库接口
 */
public interface NovelRepository {
    /**
     * 加载原始小说内容
     * @param path 文件路径
     * @return 文本行列表
     * @throws IOException IO异常
     */
    List<String> loadRaw(Path path) throws IOException;
}
