package com.novel.splitter.retrieval.impl;

import com.novel.splitter.retrieval.dto.RetrievalQuery;
import org.springframework.stereotype.Component;

/**
 * 检索查询构建器
 * <p>
 * 将用户自然语言问题转换为结构化的 RetrievalQuery。
 * 遵循严格的规则匹配，不涉及 AI 语义理解，主要用于提取特定的阅读上下文需求（如章节范围或角色意图）。
 * </p>
 */
@Component
public class RetrievalQueryBuilder {

    // 匹配“上一章”的关键词
    private static final String LAST_CHAPTER = "上一章";
    // 匹配“这一章”的关键词
    private static final String CURRENT_CHAPTER = "这一章";
    // 匹配特定对话意图的关键词
    private static final String DIALOGUE_INTENT = "他说了什么";

    /**
     * 构建查询对象（增强健壮性 + 语义清晰）
     *
     * @param question       用户输入的自然语言问题
     * @param currentChapter 当前阅读的章节号
     * @return 结构化的 RetrievalQuery 检索对象
     * @throws IllegalArgumentException 如果问题为空或仅包含空白字符，则抛出异常
     */
    public RetrievalQuery build(String question, int currentChapter) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be null or blank");
        }

        // 初始化构建器，并去除问题两端的空格
        RetrievalQuery.RetrievalQueryBuilder builder = RetrievalQuery.builder()
                .question(question.trim());

        // 解析问题中的章节范围要求
        parseChapterRange(builder, question, currentChapter);
        // 解析问题中的特定角色意图要求
        parseRole(builder, question);

        return builder.build();
    }

    /**
     * 解析问题中包含的章节范围意图，并设置到检索查询构建器中。
     * 
     * @param builder        检索查询构建器
     * @param question       用户问题
     * @param currentChapter 当前阅读章节号
     */
    private void parseChapterRange(RetrievalQuery.RetrievalQueryBuilder builder,
                                   String question,
                                   int currentChapter) {
        // 如果当前章节无效，则不设置章节限制
        if (currentChapter <= 0) {
            builder.chapterFrom(null);
            builder.chapterTo(null);
            return;
        }

        // 匹配“上一章”：限定范围为当前章节的前一章（最小为第一章）
        if (question.contains(LAST_CHAPTER)) {
            int target = Math.max(1, currentChapter - 1);
            builder.chapterFrom(target);
            builder.chapterTo(target);
        }
        // 匹配“这一章”：限定范围为当前章节
        else if (question.contains(CURRENT_CHAPTER)) {
            builder.chapterFrom(currentChapter);
            builder.chapterTo(currentChapter);
        }
        // 无匹配的章节意图，不设限制
        else {
            builder.chapterFrom(null);
            builder.chapterTo(null);
        }
    }

    /**
     * 解析问题中的角色意图，如对话检索。
     *
     * @param builder  检索查询构建器
     * @param question 用户问题
     */
    private void parseRole(RetrievalQuery.RetrievalQueryBuilder builder, String question) {
        // 如果包含“他说了什么”，将角色标记为“dialogue”（对话）
        if (question.contains(DIALOGUE_INTENT)) {
            builder.role("dialogue");
        } else {
            builder.role(null);
        }
    }
}
