package com.novel.splitter.pipeline.api;

import com.novel.splitter.pipeline.context.PipelineContext;

/**
 * 流水线阶段接口
 */
public interface Stage {
    /**
     * 执行当前阶段的逻辑
     * @param context 上下文
     */
    void process(PipelineContext context);
}
