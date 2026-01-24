package com.novel.splitter.pipeline.stages;

import com.novel.splitter.pipeline.api.Stage;
import com.novel.splitter.pipeline.context.PipelineContext;
import com.novel.splitter.repository.api.NovelRepository;

/**
 * 加载阶段
 * 从文件系统读取原始文本
 */
public class LoadStage implements Stage {
    private final NovelRepository novelRepository;

    public LoadStage(NovelRepository novelRepository) {
        this.novelRepository = novelRepository;
    }

    @Override
    public void process(PipelineContext context) {
        try {
            context.setRawLines(novelRepository.loadRaw(context.getSourceFile()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load raw file: " + context.getSourceFile(), e);
        }
    }
}
