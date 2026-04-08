package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.dto.RetrievalQuery;

import java.util.List;

/**
 * 检索服务接口
 * <p>
 * 小说问答系统中的核心 RAG 检索入口。
 * 负责协调文本嵌入（Embedding）模型与向量数据库（VectorStore），
 * 将用户查询转化为向量并在小说片段库中进行相似度检索和元数据过滤。
 * </p>
 */
public interface RetrievalService {

    /**
     * 根据构建好的查询对象执行相似度检索。
     * 
     * // 该方法会解析查询条件，调用底层向量存储进行检索，并返回匹配的小说场景片段列表
     *
     * @param query 封装了查询文本及各类过滤条件（如小说名、章节范围等）的查询对象
     * @return 与查询最匹配的小说场景片段（Scene）列表，通常按相似度降序排列
     */
    List<Scene> retrieve(RetrievalQuery query);
}
