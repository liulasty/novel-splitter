package com.novel.splitter.domain.model.llm.coze;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CozeChatRequest {
    private String bot_id;
    private String user_id;
    private List<CozeMessage> additional_messages;
    private Boolean stream;
    private Boolean auto_save_history;
}
