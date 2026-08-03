package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;

import java.util.regex.Pattern;

/** 章节识别策略：每种格式一个策略对象，可扩展注册。 */
public interface ChapterRecognitionStrategy {
    RecognitionStrategyType type();
    boolean matches(String line);
    Pattern pattern();

    static ChapterRecognitionStrategy forType(RecognitionStrategyType type, String customRegex) {
        return switch (type) {
            case CN_CHAPTER -> new PresetStrategy(RecognitionStrategyType.CN_CHAPTER,
                    "^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+章.*");
            case CN_BACK -> new PresetStrategy(RecognitionStrategyType.CN_BACK,
                    "^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+回.*");
            case CN_SECTION -> new PresetStrategy(RecognitionStrategyType.CN_SECTION,
                    "^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+节.*");
            case EN_CHAPTER -> new PresetStrategy(RecognitionStrategyType.EN_CHAPTER,
                    "(?i)^\\s*chapter\\s*\\d+.*");
            case PROLOGUE -> new PresetStrategy(RecognitionStrategyType.PROLOGUE,
                    "^\\s*(序章|楔子|引子|前言|序言).*");
            case VOLUME_CHAPTER -> new PresetStrategy(RecognitionStrategyType.VOLUME_CHAPTER,
                    "^\\s*(卷[^。！？，、\\n]{0,20}|第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+卷).*"
                            + "|^\\s*第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+章.*");
            case CUSTOM -> custom(customRegex);
        };
    }

    static ChapterRecognitionStrategy custom(String regex) {
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("CUSTOM 策略必须提供 chapterTitleRegex");
        }
        Pattern p = Pattern.compile(regex.trim());
        return new ChapterRecognitionStrategy() {
            @Override public RecognitionStrategyType type() { return RecognitionStrategyType.CUSTOM; }
            @Override public boolean matches(String line) { return p.matcher(line.trim()).matches(); }
            @Override public Pattern pattern() { return p; }
        };
    }

    record PresetStrategy(RecognitionStrategyType type, Pattern pattern) implements ChapterRecognitionStrategy {
        PresetStrategy(RecognitionStrategyType type, String regex) { this(type, Pattern.compile(regex)); }
        @Override public boolean matches(String line) {
            String t = ChapterRecognizer.stripLeadingUtf8Bom(line == null ? "" : line.trim());
            return t.length() <= 50 && pattern.matcher(t).matches();
        }
    }
}
