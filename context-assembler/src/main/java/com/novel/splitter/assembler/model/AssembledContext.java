package com.novel.splitter.assembler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组装后的上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssembledContext {
    
    /** 格式化后的上下文块列表 */
    private List<ContextBlock> blocks;
    
    /** 总 Token 数 */
    private int totalTokens;
    
    /** 是否发生截断 */
    private boolean truncated;

    /** 拼接后的纯文本（可选，方便直接使用） */
    public String getFullText() {
        if (blocks == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContextBlock block : blocks) {
            sb.append(String.format("[%s] %s\n", block.getId(), block.getContent()));
        }
        return sb.toString();
    }
}
