package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.dto.RetrievalQuery;

import java.util.List;

/**
 * 检索服务接口
 * <p>
 * 核心 RAG 检索入口，负责协调 Embedding 和 VectorStore。
 * </p>
 */
public interface RetrievalService {

    /**
     * 执行检索
     *
     * @param query 查询对象
     * @return 匹配的 Scene 列表
     */
    List<Scene> retrieve(RetrievalQuery query);
}
