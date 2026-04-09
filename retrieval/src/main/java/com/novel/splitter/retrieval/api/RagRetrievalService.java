package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.dto.RagRequest;

import java.util.List;

/**
 * RAG 检索域服务，仅负责意图分类和向量检索。
 */
public interface RagRetrievalService {

    AnswerType classify(String question);

    List<Scene> retrieve(RagRequest request);
}
