package com.novel.splitter.repository.impl;

import com.novel.splitter.infrastructure.io.FileUtils;
import com.novel.splitter.repository.api.NovelRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 本地文件系统实现的小说仓库
 */
public class LocalFileNovelRepository implements NovelRepository {
    @Override
    public List<String> loadRaw(Path path) throws IOException {
        return FileUtils.readLinesAutoDetectEncoding(path);
    }
}
