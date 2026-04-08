package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrichTaskMessage implements Serializable {
    private String parentTaskId;
    private String novelId;
    private String version;
    private List<Long> sceneIds;
}
