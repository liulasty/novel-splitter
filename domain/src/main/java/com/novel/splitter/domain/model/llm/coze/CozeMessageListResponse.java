package com.novel.splitter.domain.model.llm.coze;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CozeMessageListResponse {
    private Integer code;
    private List<CozeMessageDetail> data;

    @Data
    @NoArgsConstructor
    public static class CozeMessageDetail {
        private String id;
        private String conversation_id;
        private String bot_id;
        private String role;
        private String type; // answer, follow_up, verbose
        private String content;
        private String content_type;
    }
}
