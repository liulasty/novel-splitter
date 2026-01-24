package com.novel.splitter.llm.client.impl;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.llm.client.api.LlmClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mock LLM 客户端实现
 * <p>
 * 用于离线验证 RAG 链路。不调用真实模型，而是基于规则从 Prompt 中提取信息生成“像样”的回答。
 * </p>
 */
public class MockLlmClient implements LlmClient {

    private static final Pattern NOUN_PATTERN = Pattern.compile("\\b[A-Z][a-zA-Z]*\\b"); // 识别首字母大写或全大写的单词

    @Override
    public Answer chat(Prompt prompt) {
        if (prompt.getContextBlocks() == null || prompt.getContextBlocks().isEmpty()) {
            return Answer.builder()
                    .answer("I cannot find any relevant information in the provided context to answer your question.")
                    .citations(Collections.emptyList())
                    .confidence(0.0)
                    .build();
        }

        // 1. 提取上下文中的关键名词 (模拟"理解")
        List<String> keywords = new ArrayList<>();
        List<ContextBlock> citedBlocks = new ArrayList<>();
        
        // 简单策略：取前3个块，提取每个块的前几个大写单词
        int limit = Math.min(prompt.getContextBlocks().size(), 3);
        for (int i = 0; i < limit; i++) {
            ContextBlock block = prompt.getContextBlocks().get(i);
            citedBlocks.add(block);
            extractKeywords(block.getContent(), keywords);
        }

        if (keywords.isEmpty()) {
            keywords.add("relevant details");
            keywords.add("specific events");
        }

        // 2. 构造回答 (模板填充)
        String userQuerySummary = prompt.getUserQuestion().length() > 20 
                ? prompt.getUserQuestion().substring(0, 20) + "..." 
                : prompt.getUserQuestion();
                
        String narrative = String.format(
                "Based on the analysis of %s, it appears that %s plays a significant role regarding '%s'. " +
                "The text mentions %s in the context of %s, which directly addresses your query.",
                keywords.get(0),
                keywords.size() > 1 ? keywords.get(1) : "the character",
                userQuerySummary,
                keywords.size() > 2 ? keywords.get(2) : "this entity",
                citedBlocks.get(0).getSceneMetadata() != null ? citedBlocks.get(0).getSceneMetadata().getChapterTitle() : "the chapter"
        );

        // 3. 构造引用
        List<Answer.Citation> citations = citedBlocks.stream()
                .map(block -> {
                    String reason = "Contains mentions of " + 
                            (block.getContent().length() > 10 ? block.getContent().substring(0, 10) + "..." : "keywords");
                    return new Answer.Citation(block.getChunkId(), reason);
                })
                .collect(Collectors.toList());

        // 4. 计算置信度 (基于引用数量的简单规则)
        double confidence = 0.6 + (citedBlocks.size() * 0.1);
        confidence = Math.min(confidence, 0.95);

        return Answer.builder()
                .answer(narrative)
                .citations(citations)
                .confidence(confidence)
                .build();
    }

    private void extractKeywords(String content, List<String> keywords) {
        if (content == null) return;
        Matcher matcher = NOUN_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find() && count < 5) {
            String word = matcher.group();
            if (!keywords.contains(word)) {
                keywords.add(word);
                count++;
            }
        }
    }
}
