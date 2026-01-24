package com.novel.splitter.pipeline.impl;

import com.novel.splitter.pipeline.api.Stage;
import com.novel.splitter.pipeline.context.PipelineContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 顺序执行流水线
 */
@Slf4j
public class SequentialPipeline {
    private final List<Stage> stages = new ArrayList<>();

    public SequentialPipeline addStage(Stage stage) {
        stages.add(stage);
        return this;
    }

    public void execute(PipelineContext context) {
        log.info("Starting pipeline for novel: {}", context.getNovelName());
        
        for (Stage stage : stages) {
            String stageName = stage.getClass().getSimpleName();
            log.info("Executing stage: {}", stageName);
            try {
                long start = System.currentTimeMillis();
                stage.process(context);
                long duration = System.currentTimeMillis() - start;
                log.info("Stage {} completed in {} ms", stageName, duration);
            } catch (Exception e) {
                log.error("Stage {} failed", stageName, e);
                throw e; // Fail-fast
            }
        }
        
        log.info("Pipeline completed successfully.");
    }
}
