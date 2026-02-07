package com.novel.splitter.domain.model.llm.ollama;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaResponse {
    private String model;
    private String created_at;
    private Message message;
    private String done_reason;
    private Boolean done;
    private Long total_duration;
    private Long load_duration;
    private Long prompt_eval_count;
    private Long prompt_eval_duration;
    private Long eval_count;
    private Long eval_duration;
}
