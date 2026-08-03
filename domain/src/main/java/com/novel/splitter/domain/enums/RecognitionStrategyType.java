package com.novel.splitter.domain.enums;

import java.util.Locale;

/**
 * 章节识别策略类型（预设枚举）。
 */
public enum RecognitionStrategyType {
    /** 中文章节：识别 "第X章" 格式 */
    CN_CHAPTER,
    /** 中文回/卷："第X回" 等章回体格式 */
    CN_BACK,
    /** 中文节：识别 "第X节" 格式 */
    CN_SECTION,
    /** 英文章节：识别 Chapter X 格式 */
    EN_CHAPTER,
    /** 序章/楔子等开篇章节 */
    PROLOGUE,
    /** 卷章混合：自动检测 "卷：" 卷头，拼接全局唯一章节标题 */
    VOLUME_CHAPTER,
    /** 自定义正则：用户提供整行匹配正则 */
    CUSTOM;

    /**
     * 将前端/消息传递的字符串策略名解析为枚举。
     * <ul>
     *   <li>null 或空白 → {@link #CN_CHAPTER}（默认）</li>
     *   <li>旧值 {@code PLAIN} → {@link #CN_CHAPTER}（兼容旧前端默认值）</li>
     *   <li>其余按名称精确匹配；未知字符串抛 {@link IllegalArgumentException}</li>
     * </ul>
     */
    public static RecognitionStrategyType fromString(String strategyType) {
        if (strategyType == null || strategyType.isBlank()) {
            return RecognitionStrategyType.CN_CHAPTER;
        }
        String normalized = strategyType.trim().toUpperCase(Locale.ROOT);
        if ("PLAIN".equals(normalized)) {
            return RecognitionStrategyType.CN_CHAPTER;
        }
        try {
            return RecognitionStrategyType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "未知的章节识别策略: '" + strategyType
                            + "'（可选: CN_CHAPTER / CN_BACK / CN_SECTION / EN_CHAPTER / PROLOGUE / VOLUME_CHAPTER / CUSTOM）",
                    e);
        }
    }
}
