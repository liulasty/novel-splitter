package com.novel.splitter.application.service.rag;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import com.novel.splitter.retrieval.api.RagFacade;
import com.novel.splitter.retrieval.api.RagRetrievalService;
import com.novel.splitter.retrieval.config.RagProperties;
import com.novel.splitter.retrieval.dto.RagDebugResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagOrchestrationService implements RagFacade {

    private final RagRetrievalService ragRetrievalService;
    private final RobustLlmClient llmClient;
    private final ContextAssembler contextAssembler;
    private final RagProperties ragProperties;
    private final AssemblerConfig defaultAssemblerConfig;

    @Override
    public Answer ask(com.novel.splitter.retrieval.dto.RagRequest request) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Request");
        String question = request.getQuestion();

        AnswerType answerType = ragRetrievalService.classify(question);
        if (answerType == AnswerType.UNSUPPORTED) {
            return Answer.builder()
                    .answer("作为一个小说阅读助手，我只能回答与小说内容相关的问题哦。")
                    .citations(Collections.emptyList())
                    .confidence(1.0)
                    .build();
        }

        AssemblerConfig config = buildAssemblerConfig(request);

        try {
            stopWatch.start("1. Retrieval");
            List<Scene> scenes = ragRetrievalService.retrieve(request);
            stopWatch.stop();

            stopWatch.start("2. Context Assembly");
            List<ContextBlock> contextBlocks = contextAssembler.assemble(request.getQuestion(), scenes, config);
            stopWatch.stop();

                        String outputConstraint = buildOutputConstraint(request);
            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(request.getQuestion())
                    .contextBlocks(contextBlocks)
                    .outputConstraint(outputConstraint)
                    .build();

            stopWatch.start("3. LLM Generation");
            Answer answer;
            try {
                answer = llmClient.chat(prompt);
            } catch (Exception e) {
                log.error("LLM 生成失败: {}", e.getMessage(), e);
                answer = Answer.builder()
                        .answer("很抱歉，生成回答时出现系统错误或格式异常。")
                        .citations(Collections.emptyList())
                        .confidence(0.0)
                        .build();
            }
            stopWatch.stop();

            stopWatch.start("4. Validation");
            validateCitations(answer, contextBlocks);
            stopWatch.stop();
            return answer;
        } finally {
            log.info("RAG 请求耗时 {} ms 已完成。详情:\n{}", System.currentTimeMillis() - startTime, stopWatch.prettyPrint());
        }
    }

    @Override
    public Answer ask(String question, int topK, String novelId, String version) {
        com.novel.splitter.retrieval.dto.RagRequest r = new com.novel.splitter.retrieval.dto.RagRequest();
        r.setQuestion(question);
        r.setTopK(topK);
        r.setNovelId(novelId);
        r.setVersion(version);
        return ask(r);
    }

    @Override
    public RagDebugResponse preview(com.novel.splitter.retrieval.dto.RagRequest request) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Debug");
        Map<String, Object> stats = new HashMap<>();

        AnswerType answerType = ragRetrievalService.classify(request.getQuestion());
        if (answerType == AnswerType.UNSUPPORTED) {
            return RagDebugResponse.builder()
                    .stats(Map.of("error", "问题不受支持：作为一个小说阅读助手，我只能回答与小说内容相关的问题。"))
                    .build();
        }

        try {
            stopWatch.start("1. Retrieval");
            List<Scene> scenes = ragRetrievalService.retrieve(request);
            stopWatch.stop();
            stats.put("retrievalTimeMs", stopWatch.getLastTaskInfo().getTimeMillis());
            stats.put("retrievedCount", scenes.size());

            AssemblerConfig config = buildAssemblerConfig(request);

            stopWatch.start("2. Context Assembly");
            List<ContextBlock> contextBlocks = contextAssembler.assemble(request.getQuestion(), scenes, config);
            stopWatch.stop();
            stats.put("assemblyTimeMs", stopWatch.getLastTaskInfo().getTimeMillis());
            stats.put("contextBlockCount", contextBlocks.size());
            stats.put("totalTokens", contextBlocks.stream().mapToInt(ContextBlock::getTokenCount).sum());

                        String outputConstraint = buildOutputConstraint(request);
            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(request.getQuestion())
                    .contextBlocks(contextBlocks)
                    .outputConstraint(outputConstraint)
                    .build();

            stats.put("totalTimeMs", System.currentTimeMillis() - startTime);
            return RagDebugResponse.builder()
                    .retrievedScenes(scenes)
                    .contextBlocks(contextBlocks)
                    .finalPrompt(prompt)
                    .stats(stats)
                    .build();
        } catch (Exception e) {
            log.error("RAG 预览失败", e);
            throw new RuntimeException("RAG preview failed", e);
        }
    }

    /**
     * 根据请求参数构建输出约束，结合配置中的约束和用户指定的回答长度。
     */
    private String buildOutputConstraint(com.novel.splitter.retrieval.dto.RagRequest request) {
        String base = ragProperties.getOutputConstraint();
        if (request.getMaxAnswerTokens() != null && request.getMaxAnswerTokens() > 0) {
            String lengthHint = "回答须不超过" + request.getMaxAnswerTokens() + "字。";
            if (base != null && !base.isBlank()) {
                return base + "\n" + lengthHint;
            }
            return lengthHint;
        }
        return base;
    }

    /**
     * 根据请求参数构建 AssemblerConfig，请求中提供的值覆盖服务端默认值。
     */
    AssemblerConfig buildAssemblerConfig(com.novel.splitter.retrieval.dto.RagRequest request) {
        AssemblerConfig config = new AssemblerConfig();
        config.setMaxChunks(defaultAssemblerConfig.getMaxChunks());
        config.setMaxChunkLength(defaultAssemblerConfig.getMaxChunkLength());
        config.setMaxContextTokens(defaultAssemblerConfig.getMaxContextTokens());
        config.setReserveForAnswerTokens(defaultAssemblerConfig.getReserveForAnswerTokens());
        config.setEnableMerge(defaultAssemblerConfig.isEnableMerge());
        config.setEnableRescore(defaultAssemblerConfig.isEnableRescore());
        config.setEnableKeywordBoost(defaultAssemblerConfig.isEnableKeywordBoost());
        config.setQualityScoreWeight(defaultAssemblerConfig.getQualityScoreWeight());
        config.setExpandRadius(defaultAssemblerConfig.getExpandRadius());
        config.setExpandAcrossChapters(defaultAssemblerConfig.isExpandAcrossChapters());

        if (request.getMaxScenes() != null && request.getMaxScenes() > 0) {
            config.setMaxScenes(request.getMaxScenes());
        } else {
            config.setMaxScenes(defaultAssemblerConfig.getMaxScenes());
        }
        if (request.getMaxContextTokens() != null && request.getMaxContextTokens() > 0) {
            config.setMaxContextTokens(request.getMaxContextTokens());
        }
        return config;
    }

    private void validateCitations(Answer answer, List<ContextBlock> contextBlocks) {
        if (answer.getCitations() == null || answer.getCitations().isEmpty()) {
            return;
        }
        Map<String, ContextBlock> blockMap = contextBlocks.stream()
                .collect(Collectors.toMap(ContextBlock::getChunkId, block -> block, (a, b) -> a));
        List<Answer.Citation> validCitations = answer.getCitations().stream()
                .filter(citation -> {
                    String chunkId = citation.getChunkId();
                    if (chunkId == null) {
                        return false;
                    }
                    ContextBlock block = blockMap.get(chunkId);
                    if (block == null) {
                        return false;
                    }
                    citation.setContent(block.getContent());
                    citation.setScore(block.getScore());
                    citation.setMetadata(block.getMetadata());
                    return true;
                })
                .collect(Collectors.toList());
        answer.setCitations(validCitations);
    }
}
