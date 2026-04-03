package com.novel.splitter.application.model.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedTaskMessage implements Serializable {
    private String taskId;
    private String novelId;
    private String version;
    private List<Long> sceneIds;
}
