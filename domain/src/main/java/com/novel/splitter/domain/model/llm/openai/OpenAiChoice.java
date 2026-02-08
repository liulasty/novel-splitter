package com.novel.splitter.domain.model.llm.openai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiChoice {
    private Integer index;
    private OpenAiMessage message;
    private String finish_reason;
}
