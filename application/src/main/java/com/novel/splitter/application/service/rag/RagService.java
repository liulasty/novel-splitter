package com.novel.splitter.application.service.rag;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.dto.RagDebugResponse;
import com.novel.splitter.domain.model.dto.RetrievalQuery;
import com.novel.splitter.retrieval.api.RetrievalService;
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
 * RAG 服务
 * <p>
 * 编排检索、上下文组装和 LLM 调用，提供端到端的问答能力。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final RetrievalService retrievalService;
    private final RobustLlmClient llmClient;
    private final ContextAssembler contextAssembler;
    private final AppConfig appConfig;
    private final AssemblerConfig assemblerConfig;

    /**
     * 提出问题并获取回答 (兼容旧接口)
     */
    public Answer ask(String question, int topK) {
        return ask(question, topK, null, null);
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
    public RagDebugResponse preview(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Debug");
        Map<String, Object> stats = new HashMap<>();

        try {
            // 1. 检索 (Retrieval)
            stopWatch.start("1. Retrieval");
            AppConfig.RagConfig ragConfig = appConfig.getRag();
            int actualTopK = topK > 0 ? topK : ragConfig.getDefaultTopK();

            // Normalize novel ID
            String novelId = novel;
            if (novel != null) {
                novelId = novel.replace(".txt", "");
            }

            RetrievalQuery query = RetrievalQuery.builder()
                    .question(question)
                    .topK(actualTopK)
                    .novel(novelId)
                    .version(version)
                    .build();
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
                    .systemInstruction(ragConfig.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragConfig.getOutputConstraint())
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

    /**
     * 提出问题并获取回答
     *
     * @param question 用户问题
     * @param topK     检索数量
     * @param novel    小说名称 (可选)
     * @param version  版本 (可选)
     * @return 结构化的回答
     */
    public Answer ask(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Request");
        
        log.info("Processing RAG request: query='{}', topK={}, novel={}, version={}", question, topK, novel, version);

        try {
            // 1. 检索 (Retrieval)
            stopWatch.start("1. Retrieval");
            AppConfig.RagConfig ragConfig = appConfig.getRag();
            int actualTopK = topK > 0 ? topK : ragConfig.getDefaultTopK();
            
            // Normalize novel ID: remove .txt extension to match ingestion convention
            String novelId = novel;
            if (novel != null) {
                novelId = novel.replace(".txt", "");
            }

            RetrievalQuery query = RetrievalQuery.builder()
                    .question(question)
                    .topK(actualTopK)
                    .novel(novelId)
                    .version(version)
                    .build();
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
                    .systemInstruction(ragConfig.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragConfig.getOutputConstraint())
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

        List<String> validChunkIds = contextBlocks.stream()
                .map(ContextBlock::getChunkId)
                .collect(Collectors.toList());

        // 过滤无效引用
        List<Answer.Citation> validCitations = answer.getCitations().stream()
                .filter(citation -> {
                    if (citation.getChunkId() == null) {
                        return false;
                    }
                    boolean isValid = validChunkIds.contains(citation.getChunkId());
                    if (!isValid) {
                        log.warn("Filtered invalid citation: chunkId='{}' not found in context.", citation.getChunkId());
                    }
                    return isValid;
                })
                .collect(Collectors.toList());
        
        answer.setCitations(validCitations);
    }
}
