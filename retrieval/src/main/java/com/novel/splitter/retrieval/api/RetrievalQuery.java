package com.novel.splitter.retrieval.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索查询对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalQuery {

    /** 用户自然语言问题 */
    private String question;

    /** 小说名称 */
    private String novel;
    
    /** 版本号 */
    private String version;

    /** 起始章节号 (包含) */
    private Integer chapterFrom;

    /** 结束章节号 (包含) */
    private Integer chapterTo;

    /** 角色/功能 (e.g., "narration", "dialogue") */
    private String role;

    /** 返回结果数量 (Top-K) - 辅助字段，不在核心定义列表中，但保留以兼容现有逻辑 */
    @Builder.Default
    private int topK = 5;
}
