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
import com.novel.splitter.retrieval.dto.RagRequest;
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
    private final AssemblerConfig assemblerConfig;

    @Override
    public Answer ask(RagRequest request) {
        return ask(request.getQuestion(), request.getTopK(), request.getNovel(), request.getVersion());
    }

    @Override
    public Answer ask(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Request");

        AnswerType answerType = ragRetrievalService.classify(question);
        if (answerType == AnswerType.UNSUPPORTED) {
            return Answer.builder()
                    .answer("作为一个小说阅读助手，我只能回答与小说内容相关的问题哦。")
                    .citations(Collections.emptyList())
                    .confidence(1.0)
                    .build();
        }

        try {
            stopWatch.start("1. Retrieval");
            RagRequest retrievalRequest = new RagRequest();
            retrievalRequest.setQuestion(question);
            retrievalRequest.setTopK(topK);
            retrievalRequest.setNovel(novel);
            retrievalRequest.setVersion(version);
            List<Scene> scenes = ragRetrievalService.retrieve(retrievalRequest);
            stopWatch.stop();

            stopWatch.start("2. Context Assembly");
            List<ContextBlock> contextBlocks = contextAssembler.assemble(question, scenes, assemblerConfig);
            stopWatch.stop();

            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragProperties.getOutputConstraint())
                    .build();

            stopWatch.start("3. LLM Generation");
            Answer answer;
            try {
                answer = llmClient.chat(prompt);
            } catch (Exception e) {
                log.error("LLM generation failed: {}", e.getMessage(), e);
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
            log.info("RAG request completed in {} ms. Details:\n{}", System.currentTimeMillis() - startTime, stopWatch.prettyPrint());
        }
    }

    @Override
    public RagDebugResponse preview(RagRequest request) {
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

            stopWatch.start("2. Context Assembly");
            List<ContextBlock> contextBlocks = contextAssembler.assemble(request.getQuestion(), scenes, assemblerConfig);
            stopWatch.stop();
            stats.put("assemblyTimeMs", stopWatch.getLastTaskInfo().getTimeMillis());
            stats.put("contextBlockCount", contextBlocks.size());
            stats.put("totalTokens", contextBlocks.stream().mapToInt(ContextBlock::getTokenCount).sum());

            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(request.getQuestion())
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
                    return true;
                })
                .collect(Collectors.toList());
        answer.setCitations(validCitations);
    }
}
