package com.novel.splitter.domain.model.llm.coze;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CozeMessage {
    private String role; // user, assistant
    private String content;
    private String content_type; // text
}
