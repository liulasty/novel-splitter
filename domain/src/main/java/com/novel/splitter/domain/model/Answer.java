package com.novel.splitter.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Answer 领域模型
 * <p>
 * 封装 LLM 返回的结构化结果。
 * 必须通过 JSON Schema 严格校验，确保包含回答内容、引用来源及置信度。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Answer {

    /**
     * 自然语言回答
     * <p>
     * 对应 Schema 中的 `answer`。
     * 必须存在，如果是拒绝回答的情况，应在此字段说明原因。
     * </p>
     */
    private String answer;

    /**
     * 引用来源列表
     * <p>
     * 对应 Schema 中的 `citations`。
     * 必须存在（可为空数组），用于溯源验证。
     * </p>
     */
    private List<Citation> citations;

    /**
     * 置信度 (0.0 - 1.0)
     * <p>
     * 对应 Schema 中的 `confidence`。
     * 必须存在，系统可据此决定是否采纳该回答。
     * </p>
     */
    private Double confidence;

    /**
     * 引用详情内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        /**
         * 来源 Chunk ID
         * <p>
         * 对应 Schema 中的 `chunk_id`。
         * 用于回溯到具体的上下文块。
         * </p>
         */
        private String chunkId;

        /**
         * 引用理由
         * <p>
         * 对应 Schema 中的 `reason`。
         * 解释为何引用该块，增强可解释性。
         * </p>
         */
        private String reason;
    }
}
