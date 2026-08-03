package com.novel.splitter.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 章节识别策略预设枚举契约测试。
 */
class RecognitionStrategyTypeTest {

    @Test
    void containsExpectedPresetValues() {
        assertNotNull(RecognitionStrategyType.valueOf("CN_CHAPTER"));
        assertNotNull(RecognitionStrategyType.valueOf("CN_BACK"));
        assertNotNull(RecognitionStrategyType.valueOf("CN_SECTION"));
        assertNotNull(RecognitionStrategyType.valueOf("EN_CHAPTER"));
        assertNotNull(RecognitionStrategyType.valueOf("PROLOGUE"));
        assertNotNull(RecognitionStrategyType.valueOf("VOLUME_CHAPTER"));
        assertNotNull(RecognitionStrategyType.valueOf("CUSTOM"));
    }

    @Test
    void doesNotExposePlainOrAuto() {
        Set<String> names = Arrays.stream(RecognitionStrategyType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertFalse(names.contains("PLAIN"), "PLAIN 已被移除，不应再暴露");
        assertFalse(names.contains("AUTO"), "不提供 AUTO 自动检测");
        assertTrue(names.contains("CUSTOM"));
    }
}
