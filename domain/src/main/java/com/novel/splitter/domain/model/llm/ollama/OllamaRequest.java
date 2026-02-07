package com.novel.splitter.domain.model.llm.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {
    private String model;
    private List<Message> messages;
    private String format;
    private Boolean stream;
    private Options options;
}
