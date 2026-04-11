package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import com.novel.splitter.retrieval.api.RagRetrievalService;
import com.novel.splitter.retrieval.api.RetrievalService;
import com.novel.splitter.retrieval.config.RagProperties;
import com.novel.splitter.retrieval.dto.RagRequest;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagServiceImpl implements RagRetrievalService {

    private final RetrievalService retrievalService;
    private final RagProperties ragProperties;
    private final AnswerPolicyClassifier policyClassifier;
    private final RetrievalQueryBuilder queryBuilder;

    @Override
    public AnswerType classify(String question) {
        return policyClassifier.classify(question);
    }

    @Override
    public List<Scene> retrieve(RagRequest request) {
        String question = request.getQuestion();
        int topK = normalizeTopK(request.getTopK());
        String novelId = request.getNovelId();
        String version = request.getVersion();

        log.info("Retrieval request: question='{}', topK={}, novelId={}, version={}, chunk={}/{}",
                question, topK, novelId, version, request.getChunkSize(), request.getChunkOverlap());

        int currentChapter = -1;
        RetrievalQuery query = queryBuilder.build(question, currentChapter);
        query.setTopK(topK);
        query.setNovelId(novelId);
        query.setVersion(version);
        query.setChunkSize(request.getChunkSize());
        query.setChunkOverlap(request.getChunkOverlap());
        return retrievalService.retrieve(query);
    }

    /**
     * 规范化 TopK 值
     *
     * @param topK 用户请求中的检索数量
     * @return 如果请求有效则返回该值，否则返回配置默认值或硬编码的默认值 3
     */
    private int normalizeTopK(int topK) {
        if (topK > 0) {
            return topK;
        }
        if (ragProperties != null && ragProperties.getDefaultTopK() > 0) {
            return ragProperties.getDefaultTopK();
        }
        return 3;
    }
}
