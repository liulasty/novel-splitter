package com.novel.splitter.domain.model.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String question;
    private int topK = 3;
    private String novel;
    private String version;
}
