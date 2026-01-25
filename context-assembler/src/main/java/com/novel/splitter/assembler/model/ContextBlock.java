package com.novel.splitter.assembler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下文块
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextBlock {
    
    /** 稳定编号 (C1, C2...) */
    private String id;
    
    /** 原始内容 */
    private String content;
    
    /** 来源章节 */
    private Integer chapterIndex;
    
    /** 来源段落 */
    private Integer paragraphIndex;
}
