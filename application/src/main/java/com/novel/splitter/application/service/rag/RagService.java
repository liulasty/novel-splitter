package com.novel.splitter.application.service.rag;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.retrieval.api.RetrievalQuery;
import com.novel.splitter.retrieval.api.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
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
    private final ContextAssembler contextAssembler;
    private final LlmClient llmClient;

    /**
     * 提出问题并获取回答 (兼容旧接口)
     */
    public Answer ask(String question, int topK) {
        return ask(question, topK, null, null);
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
        log.info("Processing RAG request: query='{}', topK={}, novel={}, version={}", question, topK, novel, version);

        // 1. 检索 (Retrieval)
        RetrievalQuery query = RetrievalQuery.builder()
                .question(question)
                .topK(topK)
                .novel(novel)
                .version(version)
                .build();
        List<Scene> scenes = retrievalService.retrieve(query);
        log.info("Retrieved {} scenes", scenes.size());

        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            log.info("Scene[{}]: ID={}, Chapter={}, Metadata={}", i, scene.getId(), scene.getChapterTitle(), scene.getMetadata());
            log.info("Scene[{}] Content:\n{}", i, scene.getText());
        }

        // 2. 组装上下文 (Context Assembly)
        // 注意：ContextAssembler 目前返回 String，但我们需要 List<ContextBlock>。
        // 由于 ContextAssembler 设计时主要为了纯文本拼接，这里我们需要手动转换一下，
        // 或者后续升级 ContextAssembler。
        // 暂时策略：手动将 Scene 转换为 ContextBlock，直接构建 Prompt。
        // 理由：ContextAssembler.assemble() 返回的是 String，适合直接丢给不支持结构化的旧模型。
        // 但 Prompt 对象需要 List<ContextBlock>。我们在这里做适配。
        
        List<ContextBlock> contextBlocks = scenes.stream()
                .map(this::convertToContextBlock)
                .collect(Collectors.toList());

        // 3. 构建 Prompt
        Prompt prompt = Prompt.builder()
                .systemInstruction("你是一个专门分析小说内容的智能助手。" +
                        "你的任务是仅根据提供的上下文块回答用户的问题。" +
                        "综合相关细节提供全面的回答。\n" +
                        "如果上下文中没有答案，请明确说明你不知道。\n\n" +
                        "### 响应格式 ###\n" +
                        "你必须以有效的 JSON 格式返回结果。不要返回 Markdown 或代码块。\n" +
                        "JSON 对象必须严格遵循以下 Schema：\n" +
                        "{\n" +
                        "  \"answer\": \"(string) 对问题的详细回答\",\n" +
                        "  \"citations\": [\n" +
                        "    {\n" +
                        "      \"chunkId\": \"(string) 引用来源的 chunkId (必须与上下文中提供的 ID 完全一致)\",\n" +
                        "      \"reason\": \"(string) 简要解释为何该块相关\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"confidence\": (number) 0.0 到 1.0 表示置信度\n" +
                        "}\n" +
                        "禁止包含其他字段。绝对禁止返回原始上下文块（如 content 字段）。")
                .userQuestion(question)
                .contextBlocks(contextBlocks)
                .outputConstraint("JSON 对象，包含字段：answer, citations (list of {chunkId, reason}), confidence。")
                .build();

        // 4. LLM 生成 (Generation)
        Answer answer;
        try {
            answer = llmClient.chat(prompt);
        } catch (Exception e) {
            log.error("LLM generation failed or returned invalid format: {}", e.getMessage());
            // 兜底默认对象
            answer = Answer.builder()
                    .answer("很抱歉，生成回答时出现系统错误或格式异常。")
                    .citations(Collections.emptyList())
                    .confidence(0.0)
                    .build();
        }
        
        log.info("Generated answer with confidence: {}", answer.getConfidence());
        log.info("Full Answer Object: {}", answer);

        // 5. 校验引用完整性 (Validation)
        validateCitations(answer, contextBlocks);

        return answer;
    }

    private void validateCitations(Answer answer, List<ContextBlock> contextBlocks) {
        if (answer.getCitations() == null || answer.getCitations().isEmpty()) {
            return;
        }

        List<String> validChunkIds = contextBlocks.stream()
                .map(ContextBlock::getChunkId)
                .collect(Collectors.toList());

        for (Answer.Citation citation : answer.getCitations()) {
            if (!validChunkIds.contains(citation.getChunkId())) {
                log.error("Invalid citation detected: chunkId='{}' not found in context blocks.", citation.getChunkId());
                throw new IllegalStateException("LLM cited a non-existent chunkId: " + citation.getChunkId());
            }
        }
    }

    private ContextBlock convertToContextBlock(Scene scene) {
        return ContextBlock.builder()
                .chunkId(scene.getId())
                .content(scene.getText())
                .sceneMetadata(scene.getMetadata())
                .build();
    }
}
