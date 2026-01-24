package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Scene 元数据
 * <p>
 * 存储关于 Scene 的辅助信息，如涉及的人物、地点、时间等。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneMetadata {
    /**
     * 出现的人物列表（预留）
     */
    private List<String> characters;

    /**
     * 场景地点（预留）
     */
    private String location;

    /**
     * 时间信息（预留）
     */
    private String time;

    /**
     * 扩展字段
     */
    private Map<String, Object> extra;
}
