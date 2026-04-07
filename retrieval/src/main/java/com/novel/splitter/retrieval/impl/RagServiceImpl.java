package com.novel.splitter.retrieval.impl;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.dto.RagDebugResponse;
import com.novel.splitter.retrieval.dto.RagRequest;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import com.novel.splitter.retrieval.api.RagFacade;
import com.novel.splitter.retrieval.api.RetrievalService;
import com.novel.splitter.retrieval.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 服务实现
 * <p>
 * 编排检索、上下文组装和 LLM 调用，提供端到端的问答能力。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagServiceImpl implements RagFacade {

    private final RetrievalService retrievalService;
    private final RobustLlmClient llmClient;
    private final ContextAssembler contextAssembler;
    private final RagProperties ragProperties;
    private final AssemblerConfig assemblerConfig;
    private final AnswerPolicyClassifier policyClassifier;
    private final RetrievalQueryBuilder queryBuilder;

    @Override
    public Answer ask(RagRequest request) {
        return ask(request.getQuestion(), normalizeTopK(request.getTopK()), request.getNovel(), request.getVersion());
    }

    @Override
    public RagDebugResponse preview(RagRequest request) {
        return preview(request.getQuestion(), normalizeTopK(request.getTopK()), request.getNovel(), request.getVersion());
    }

    /**
     * RAG 调试/预览 (不调用 LLM)
     *
     * @param question 用户问题
     * @param topK     检索数量
     * @param novel    小说名称 (可选)
     * @param version  版本 (可选)
     * @return 调试信息
     */
    private RagDebugResponse preview(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Debug");
        Map<String, Object> stats = new HashMap<>();

        // 0. 前置意图拦截
        AnswerType answerType = policyClassifier.classify(question);
        if (answerType == AnswerType.UNSUPPORTED) {
            log.info("Preview blocked by policy classifier: {}", question);
            return RagDebugResponse.builder()
                    .stats(Map.of("error", "问题不受支持：作为一个小说阅读助手，我只能回答与小说内容相关的问题。"))
                    .build();
        }

        try {
            // 1. 检索 (Retrieval)
            stopWatch.start("1. Retrieval");
            int actualTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();

            // Normalize novel ID
            String novelId = novel;
            if (novel != null) {
                novelId = novel.replace(".txt", "");
            }

            // 使用 QueryBuilder 增强查询
            int currentChapter = -1; // 暂无当前阅读章节信息
            RetrievalQuery query = queryBuilder.build(question, currentChapter);
            query.setTopK(actualTopK);
            query.setNovel(novelId);
            query.setVersion(version);

            List<Scene> scenes = retrievalService.retrieve(query);
            stopWatch.stop();
            stats.put("retrievalTimeMs", stopWatch.getLastTaskTimeMillis());
            stats.put("retrievedCount", scenes.size());

            // 2. 组装上下文 (Context Assembly)
            stopWatch.start("2. Context Assembly");
            List<ContextBlock> contextBlocks = contextAssembler.assemble(question, scenes, assemblerConfig);
            stopWatch.stop();
            stats.put("assemblyTimeMs", stopWatch.getLastTaskTimeMillis());
            stats.put("contextBlockCount", contextBlocks.size());
            stats.put("totalTokens", contextBlocks.stream().mapToInt(ContextBlock::getTokenCount).sum());

            // 3. 构建 Prompt
            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragProperties.getOutputConstraint())
                    .build();

            stats.put("totalTimeMs", System.currentTimeMillis() - startTime);

            return RagDebugResponse.builder()
                    .retrievedScenes(scenes)
                    .contextBlocks(contextBlocks)
                    .finalPrompt(prompt)
                    .stats(stats)
                    .build();

        } catch (Exception e) {
            log.error("RAG preview failed", e);
            throw new RuntimeException("RAG preview failed", e);
        }
    }

    @Override
    public Answer ask(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Request");
        
        log.info("Processing RAG request: query='{}', topK={}, novel={}, version={}", question, topK, novel, version);

        // 0. 前置意图拦截
        AnswerType answerType = policyClassifier.classify(question);
        if (answerType == AnswerType.UNSUPPORTED) {
            log.info("Question blocked by policy classifier: {}", question);
            return Answer.builder()
                    .answer("作为一个小说阅读助手，我只能回答与小说内容相关的问题哦。")
                    .citations(Collections.emptyList())
                    .confidence(1.0)
                    .build();
        }

        try {
            // 1. 检索 (Retrieval)
            stopWatch.start("1. Retrieval");
            int actualTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();
            
            // Normalize novel ID: remove .txt extension to match ingestion convention
            String novelId = novel;
            if (novel != null) {
                novelId = novel.replace(".txt", "");
            }

            // 使用 QueryBuilder 增强查询
            int currentChapter = -1; // 暂无当前阅读章节信息
            RetrievalQuery query = queryBuilder.build(question, currentChapter);
            query.setTopK(actualTopK);
            query.setNovel(novelId);
            query.setVersion(version);

            List<Scene> scenes = retrievalService.retrieve(query);
            stopWatch.stop();
            log.info("Retrieved {} scenes", scenes.size());

            // 2. 组装上下文 (Context Assembly)
            stopWatch.start("2. Context Assembly");
            // 使用新版 ContextAssembler 进行流水线处理
            List<ContextBlock> contextBlocks = contextAssembler.assemble(question, scenes, assemblerConfig);
            stopWatch.stop();

            // 3. 构建 Prompt
            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragProperties.getOutputConstraint())
                    .build();

            // 4. LLM 生成 (Generation)
            stopWatch.start("3. LLM Generation");
            Answer answer;
            try {
                answer = llmClient.chat(prompt);
            } catch (Exception e) {
                log.error("LLM generation failed: {}", e.getMessage());
                // 兜底默认对象
                answer = Answer.builder()
                        .answer("很抱歉，生成回答时出现系统错误或格式异常。")
                        .citations(Collections.emptyList())
                        .confidence(0.0)
                        .build();
            }
            stopWatch.stop();
            
            log.info("Generated answer with confidence: {}", answer.getConfidence());

            // 5. 校验引用完整性 (Validation)
            stopWatch.start("4. Validation");
            validateCitations(answer, contextBlocks);
            stopWatch.stop();

            return answer;
        } finally {
            log.info("RAG request completed in {} ms. Details:\n{}", System.currentTimeMillis() - startTime, stopWatch.prettyPrint());
        }
    }

    private void validateCitations(Answer answer, List<ContextBlock> contextBlocks) {
        if (answer.getCitations() == null || answer.getCitations().isEmpty()) {
            return;
        }

        // 1. 构建 Map 加速查找
        Map<String, ContextBlock> blockMap = contextBlocks.stream()
                .collect(Collectors.toMap(ContextBlock::getChunkId, block -> block, (a, b) -> a));

        // 2. 过滤并回填
        List<Answer.Citation> validCitations = answer.getCitations().stream()
                .filter(citation -> {
                    String chunkId = citation.getChunkId();
                    if (chunkId == null) {
                        return false;
                    }
                    
                    ContextBlock block = blockMap.get(chunkId);
                    if (block == null) {
                        log.warn("Filtered invalid citation: chunkId='{}' not found in context.", chunkId);
                        return false;
                    }

                    // 回填信息
                    citation.setContent(block.getContent());
                    citation.setScore(block.getScore());
                    return true;
                })
                .collect(Collectors.toList());
        
        answer.setCitations(validCitations);
    }

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
