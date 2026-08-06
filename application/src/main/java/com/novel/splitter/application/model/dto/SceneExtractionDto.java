package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** LLM 抽取的单场景语义标注结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneExtractionDto {
    /** 场景 chunkId（对应 Scene.id），用于与场景行匹配 */
    private String id;
    private List<String> characters;
    private String location;
    private String time;
    private String role;
}
