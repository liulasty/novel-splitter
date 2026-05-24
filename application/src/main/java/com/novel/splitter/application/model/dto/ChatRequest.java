package com.novel.splitter.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "问题不能为空")
    private String question;

    @Min(value = 1, message = "topK 必须大于 0")
    private int topK = 3;
    private String novelId;
    private String version;
    /** 可选；同一 version 下多数据集时必须提供 */
    private Integer chunkSize;
    private Integer chunkOverlap;
    /** 上下文场景数上限（覆盖服务端默认），≤0 表示使用服务端默认 */
    private Integer maxScenes;
    /** 上下文 Token 预算（覆盖服务端默认），≤0 表示使用服务端默认 */
    private Integer maxContextTokens;
    /** 回答目标 Token 数，≤0 表示不限制；会影响提示词中的输出长度约束 */
    private Integer maxAnswerTokens;
}
