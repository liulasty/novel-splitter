package com.novel.splitter.domain.enums;

/**
 * 章节识别策略类型。
 */
public enum RecognitionStrategyType {
    /** 普通单卷：仅识别 "第X章" 格式，卷信息不处理 */
    PLAIN,
    /** 卷章混合：自动检测 "卷：" 卷头，拼接全局唯一章节标题 */
    VOLUME_CHAPTER,
    /** 自定义正则：用户提供整行匹配正则 */
    CUSTOM
}
