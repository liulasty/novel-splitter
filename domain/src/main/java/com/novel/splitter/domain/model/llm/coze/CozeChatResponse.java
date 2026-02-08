package com.novel.splitter.domain.model.llm.coze;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CozeChatResponse {
    private Integer code;
    private String msg;
    private CozeChatData data;

    @Data
    @NoArgsConstructor
    public static class CozeChatData {
        private String id;
        private String conversation_id;
        private String bot_id;
        private String status; // created, in_progress, completed, failed, requires_action
        private Object last_error;
    }
}
