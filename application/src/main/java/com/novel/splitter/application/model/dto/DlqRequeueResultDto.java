package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqRequeueResultDto {
    private String queueName;
    private int requeued;
    private long remaining;
}
