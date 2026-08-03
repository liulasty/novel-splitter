package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChapterRecognitionStrategyRegistryTest {

    private final ChapterRecognitionStrategyRegistry registry = new ChapterRecognitionStrategyRegistry();

    @Test
    void everyEnumValueHasAStrategy() {
        for (RecognitionStrategyType type : RecognitionStrategyType.values()) {
            assertDoesNotThrow(() -> registry.require(type, "第1話 起始"),
                    () -> "require(" + type + ") should resolve");
        }
    }

    @Test
    void resolveThrowsWhenCustomMissingRegex() {
        assertThrows(IllegalArgumentException.class, () -> registry.require(RecognitionStrategyType.CUSTOM, null));
    }

    @Test
    void requireReturnsSameInstanceForPresetStrategy() {
        ChapterRecognitionStrategy a = registry.require(RecognitionStrategyType.CN_CHAPTER, null);
        ChapterRecognitionStrategy b = registry.require(RecognitionStrategyType.CN_CHAPTER, null);
        assertSame(a, b, "preset strategies should be cached singletons");
    }
}
