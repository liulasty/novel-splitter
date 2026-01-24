package com.novel.splitter.validation.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.validation.api.SceneValidator;
import com.novel.splitter.validation.api.ValidationResult;

import java.util.List;

/**
 * 长度校验器
 * 检查 Scene 的字数是否在合理范围内。
 */
public class LengthValidator implements SceneValidator {

    private final int minLength;
    private final int maxLength;

    public LengthValidator(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public ValidationResult validate(List<Scene> scenes) {
        ValidationResult result = new ValidationResult();

        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            int length = scene.getWordCount();

            if (length == 0) {
                result.addError(String.format("Scene[%d] (ID: %s) is empty.", i, scene.getId()));
            } else if (length < minLength) {
                result.addWarning(String.format("Scene[%d] is too short (%d words < %d). Chapter: %s", 
                        i, length, minLength, scene.getChapterTitle()));
            } else if (length > maxLength) {
                // 超过最大长度通常不仅是 Warning，可能是切分失效
                result.addWarning(String.format("Scene[%d] is too long (%d words > %d). Chapter: %s", 
                        i, length, maxLength, scene.getChapterTitle()));
            }
        }

        return result;
    }
}
