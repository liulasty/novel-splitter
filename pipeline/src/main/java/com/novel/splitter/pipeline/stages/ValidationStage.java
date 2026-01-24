package com.novel.splitter.pipeline.stages;

import com.novel.splitter.pipeline.api.Stage;
import com.novel.splitter.pipeline.context.PipelineContext;
import com.novel.splitter.validation.api.SceneValidator;
import com.novel.splitter.validation.api.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验阶段
 */
@Slf4j
public class ValidationStage implements Stage {
    private final List<SceneValidator> validators = new ArrayList<>();

    public ValidationStage addValidator(SceneValidator validator) {
        this.validators.add(validator);
        return this;
    }

    @Override
    public void process(PipelineContext context) {
        log.info("Starting validation for {} scenes...", context.getScenes().size());
        
        ValidationResult totalResult = new ValidationResult();
        
        for (SceneValidator validator : validators) {
            ValidationResult result = validator.validate(context.getScenes());
            totalResult.merge(result);
        }

        // 记录警告
        for (String warning : totalResult.getWarnings()) {
            log.warn("[Validation Warning] {}", warning);
        }

        // 处理错误
        if (!totalResult.isPassed()) {
            for (String error : totalResult.getErrors()) {
                log.error("[Validation Error] {}", error);
            }
            // 策略：遇到错误是终止 Pipeline 还是仅记录？
            // 这里选择抛出异常终止，因为"错误"意味着数据不可用
            throw new RuntimeException("Validation failed with " + totalResult.getErrors().size() + " errors.");
        }

        log.info("Validation passed.");
    }
}
