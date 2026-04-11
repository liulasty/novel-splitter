package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqStatDto {
    private String queueName;
    /** 重投主交换 {@code novel.task.exchange} 时使用的 routing key（如 load、split、embed）。 */
    private String targetRoutingKey;
    private long messageCount;
}
