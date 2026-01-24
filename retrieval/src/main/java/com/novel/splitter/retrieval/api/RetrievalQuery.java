package com.novel.splitter.retrieval.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索查询对象
 * <p>
 * 封装检索请求参数，为未来扩展过滤条件（如角色、章节范围）做准备。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalQuery {

    /** 用户问题 / 查询文本 */
    private String text;

    /** 返回结果数量 (Top-K) */
    @Builder.Default
    private int topK = 5;

    // === 预留过滤字段 ===
    
    /** 角色/功能 (e.g., "narration", "dialogue") */
    private String role;
    
    /** 小说名称 */
    private String novel;
}
