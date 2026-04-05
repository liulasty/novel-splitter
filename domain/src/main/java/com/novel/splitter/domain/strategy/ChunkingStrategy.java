package com.novel.splitter.domain.strategy;

import com.novel.splitter.domain.model.Scene;
import java.util.List;

/**
 * 文本切分策略接口
 */
public interface ChunkingStrategy {
    
    /**
     * 将父场景切分为多个子场景
     *
     * @param parent 父场景
     * @return 切分后的子场景列表
     */
    List<Scene> split(Scene parent);
}
