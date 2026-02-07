package com.novel.splitter.domain.model.llm.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Options {
    private Double temperature;

    @JsonProperty("num_ctx")
    private Integer numCtx;

    @JsonProperty("num_predict")
    private Integer numPredict;

    @JsonProperty("num_thread")
    private Integer numThread;

    @JsonProperty("num_gpu")
    private Integer numGpu;
}
