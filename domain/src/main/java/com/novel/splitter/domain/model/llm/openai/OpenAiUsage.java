package com.novel.splitter.domain.model.llm.openai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiUsage {
    private Integer prompt_tokens;
    private Integer completion_tokens;
    private Integer total_tokens;
}
