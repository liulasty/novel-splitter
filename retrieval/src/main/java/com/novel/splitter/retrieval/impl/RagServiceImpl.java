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
 * RAG 服务实现类
 * <p>
 * 编排检索、上下文组装和 LLM 调用，提供端到端的问答能力。
 * 该类作为整个检索增强生成（RAG）流程的入口，处理请求解析、向量检索、Prompt 组装、模型调用及结果后处理。
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

    /**
     * 执行 RAG 问答请求
     *
     * @param request 包含用户问题、检索数量、小说ID及版本等信息的请求对象
     * @return 包含生成的回答内容及引用信息的 Answer 对象
     */
    @Override
    public Answer ask(RagRequest request) {
        // 调用底层的 ask 方法，并对 topK 进行规范化处理
        return ask(request.getQuestion(), normalizeTopK(request.getTopK()), request.getNovel(), request.getVersion());
    }

    /**
     * 预览 RAG 执行流程（仅进行检索和 Prompt 组装，不调用大语言模型）
     *
     * @param request 包含用户问题、检索数量、小说ID及版本等信息的请求对象
     * @return 包含检索片段、组装好的上下文、最终 Prompt 及统计信息的调试响应对象
     */
    @Override
    public RagDebugResponse preview(RagRequest request) {
        // 调用底层的 preview 方法，并对 topK 进行规范化处理
        return preview(request.getQuestion(), normalizeTopK(request.getTopK()), request.getNovel(), request.getVersion());
    }

    /**
     * RAG 调试/预览 (不调用 LLM)
     *
     * @param question 用户问题
     * @param topK     检索数量
     * @param novel    小说名称或ID (可选)
     * @param version  版本信息 (可选)
     * @return 调试信息，包含检索结果和最终组装的 Prompt
     */
    private RagDebugResponse preview(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Debug");
        Map<String, Object> stats = new HashMap<>();

        // 0. 前置意图拦截：检查用户问题是否属于支持回答的范畴
        AnswerType answerType = policyClassifier.classify(question);
        if (answerType == AnswerType.UNSUPPORTED) {
            log.info("Preview blocked by policy classifier: {}", question);
            return RagDebugResponse.builder()
                    .stats(Map.of("error", "问题不受支持：作为一个小说阅读助手，我只能回答与小说内容相关的问题。"))
                    .build();
        }

        try {
            // 1. 检索 (Retrieval)：根据问题在向量库中查找相关的文本片段
            stopWatch.start("1. Retrieval");
            int actualTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();

            // Normalize novel ID: 移除 .txt 后缀以匹配数据注入时的约定
            String novelId = novel;
            if (novel != null) {
                novelId = novel.replace(".txt", "");
            }

            // 使用 QueryBuilder 增强查询，解析出结构化的检索条件
            int currentChapter = -1; // 暂无当前阅读章节信息
            RetrievalQuery query = queryBuilder.build(question, currentChapter);
            query.setTopK(actualTopK);
            query.setNovel(novelId);
            query.setVersion(version);

            // 执行检索
            List<Scene> scenes = retrievalService.retrieve(query);
            stopWatch.stop();
            stats.put("retrievalTimeMs", stopWatch.getLastTaskTimeMillis());
            stats.put("retrievedCount", scenes.size());

            // 2. 组装上下文 (Context Assembly)：将检索到的片段转换为 LLM 可理解的上下文块
            stopWatch.start("2. Context Assembly");
            List<ContextBlock> contextBlocks = contextAssembler.assemble(question, scenes, assemblerConfig);
            stopWatch.stop();
            stats.put("assemblyTimeMs", stopWatch.getLastTaskTimeMillis());
            stats.put("contextBlockCount", contextBlocks.size());
            stats.put("totalTokens", contextBlocks.stream().mapToInt(ContextBlock::getTokenCount).sum());

            // 3. 构建 Prompt：结合系统指令、用户问题和上下文，生成最终发送给 LLM 的提示词
            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragProperties.getOutputConstraint())
                    .build();

            stats.put("totalTimeMs", System.currentTimeMillis() - startTime);

            // 返回预览结果
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
     * 核心 RAG 问答处理流程
     *
     * @param question 用户问题
     * @param topK     检索返回的最大片段数
     * @param novel    小说名称或ID (可选)
     * @param version  版本信息 (可选)
     * @return 包含 LLM 回答及引用来源的 Answer 对象
     */
    @Override
    public Answer ask(String question, int topK, String novel, String version) {
        long startTime = System.currentTimeMillis();
        StopWatch stopWatch = new StopWatch("RAG Request");
        
        log.info("Processing RAG request: query='{}', topK={}, novel={}, version={}", question, topK, novel, version);

        // 0. 前置意图拦截：过滤掉不支持的闲聊或与小说无关的问题
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
            // 1. 检索 (Retrieval)：获取最相关的文本片段
            stopWatch.start("1. Retrieval");
            int actualTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();
            
            // Normalize novel ID: remove .txt extension to match ingestion convention
            String novelId = novel;
            if (novel != null) {
                novelId = novel.replace(".txt", "");
            }

            // 使用 QueryBuilder 增强查询，提取可能存在的章节限制等
            int currentChapter = -1; // 暂无当前阅读章节信息
            RetrievalQuery query = queryBuilder.build(question, currentChapter);
            query.setTopK(actualTopK);
            query.setNovel(novelId);
            query.setVersion(version);

            // 调用检索服务获取 Scene 列表
            List<Scene> scenes = retrievalService.retrieve(query);
            stopWatch.stop();
            log.info("Retrieved {} scenes", scenes.size());

            // 2. 组装上下文 (Context Assembly)：对片段进行排序、截断或格式化处理
            stopWatch.start("2. Context Assembly");
            // 使用新版 ContextAssembler 进行流水线处理
            List<ContextBlock> contextBlocks = contextAssembler.assemble(question, scenes, assemblerConfig);
            stopWatch.stop();

            // 3. 构建 Prompt：生成 LLM 提示词
            Prompt prompt = Prompt.builder()
                    .systemInstruction(ragProperties.getSystemInstruction())
                    .userQuestion(question)
                    .contextBlocks(contextBlocks)
                    .outputConstraint(ragProperties.getOutputConstraint())
                    .build();

            // 4. LLM 生成 (Generation)：调用大模型生成回答
            stopWatch.start("3. LLM Generation");
            Answer answer;
            try {
                answer = llmClient.chat(prompt);
            } catch (Exception e) {
                log.error("LLM generation failed: {}", e.getMessage());
                // 兜底默认对象，防止异常抛出导致整个请求失败
                answer = Answer.builder()
                        .answer("很抱歉，生成回答时出现系统错误或格式异常。")
                        .citations(Collections.emptyList())
                        .confidence(0.0)
                        .build();
            }
            stopWatch.stop();
            
            log.info("Generated answer with confidence: {}", answer.getConfidence());

            // 5. 校验引用完整性 (Validation)：确保大模型返回的引用存在于检索上下文中
            stopWatch.start("4. Validation");
            validateCitations(answer, contextBlocks);
            stopWatch.stop();

            return answer;
        } finally {
            // 打印性能统计日志
            log.info("RAG request completed in {} ms. Details:\n{}", System.currentTimeMillis() - startTime, stopWatch.prettyPrint());
        }
    }

    /**
     * 校验并补全 LLM 生成的引用信息
     *
     * @param answer        大语言模型生成的包含回答及引用的对象
     * @param contextBlocks 提供给 LLM 的上下文块集合
     */
    private void validateCitations(Answer answer, List<ContextBlock> contextBlocks) {
        if (answer.getCitations() == null || answer.getCitations().isEmpty()) {
            return;
        }

        // 1. 构建 Map 加速查找，key 为 chunkId
        Map<String, ContextBlock> blockMap = contextBlocks.stream()
                .collect(Collectors.toMap(ContextBlock::getChunkId, block -> block, (a, b) -> a));

        // 2. 过滤无效引用并回填具体内容和相关性得分
        List<Answer.Citation> validCitations = answer.getCitations().stream()
                .filter(citation -> {
                    String chunkId = citation.getChunkId();
                    if (chunkId == null) {
                        return false; // 忽略缺少 chunkId 的引用
                    }
                    
                    ContextBlock block = blockMap.get(chunkId);
                    if (block == null) {
                        log.warn("Filtered invalid citation: chunkId='{}' not found in context.", chunkId);
                        return false; // 过滤掉伪造或不存在的引用
                    }

                    // 回填原始片段的内容与分数信息
                    citation.setContent(block.getContent());
                    citation.setScore(block.getScore());
                    return true;
                })
                .collect(Collectors.toList());
        
        answer.setCitations(validCitations);
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
        return 3; // 最终的硬编码兜底值
    }
}
