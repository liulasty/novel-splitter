package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** 章节识别策略注册表：构造时预置除 CUSTOM 外的全部枚举策略，运行时按类型分发。 */
@Component
public class ChapterRecognitionStrategyRegistry {

    private final Map<RecognitionStrategyType, ChapterRecognitionStrategy> strategies =
            new EnumMap<>(RecognitionStrategyType.class);

    public ChapterRecognitionStrategyRegistry() {
        for (RecognitionStrategyType type : RecognitionStrategyType.values()) {
            if (type != RecognitionStrategyType.CUSTOM) {
                strategies.put(type, ChapterRecognitionStrategy.forType(type, null));
            }
        }
    }

    /**
     * 按类型获取策略；CUSTOM 需要调用方提供 chapterTitleRegex，其余类型从预置表取。
     *
     * @throws IllegalArgumentException CUSTOM 未提供正则，或类型未注册时抛出
     */
    public ChapterRecognitionStrategy require(RecognitionStrategyType type, String customRegex) {
        if (type == RecognitionStrategyType.CUSTOM) {
            return ChapterRecognitionStrategy.custom(customRegex);
        }
        ChapterRecognitionStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("未注册的章节识别策略类型: " + type);
        }
        return strategy;
    }
}
