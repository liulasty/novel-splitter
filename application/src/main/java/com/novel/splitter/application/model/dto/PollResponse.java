package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollResponse {
    private String taskId;
    private String status;
    private int progress;
    private String message;
    private long updatedAt;
    private long serverTime;
}
