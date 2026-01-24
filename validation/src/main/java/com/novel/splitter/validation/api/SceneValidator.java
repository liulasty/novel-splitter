package com.novel.splitter.validation.api;

import com.novel.splitter.domain.model.Scene;
import java.util.List;

/**
 * Scene 校验器接口
 */
public interface SceneValidator {
    /**
     * 执行校验
     * @param scenes 待校验的 Scene 列表
     * @return 校验结果
     */
    ValidationResult validate(List<Scene> scenes);
}
