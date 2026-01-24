package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 强类型 Prompt 领域模型
 * <p>
 * 用于封装发送给 LLM 的完整请求上下文。
 * 采用结构化设计，避免过早进行 String 拼接，以适应不同的 LLM API 格式要求。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prompt {
    /** 系统指令 (System Message) */
    private String systemInstruction;

    /** 上下文证据块列表 */
    private List<ContextBlock> contextBlocks;

    /** 用户问题 (User Query) */
    private String userQuestion;

    /** 输出约束 (Output Format/Constraints) */
    private String outputConstraint;
}
