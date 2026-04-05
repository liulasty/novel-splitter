package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.dto.RetrievalQuery;
import org.springframework.stereotype.Component;

/**
 * 检索查询构建器
 * <p>
 * 将用户自然语言问题转换为结构化的 RetrievalQuery。
 * 遵循严格的规则匹配，不涉及 AI 语义理解。
 * </p>
 */
@Component
public class RetrievalQueryBuilder {

    private static final String LAST_CHAPTER = "上一章";
    private static final String CURRENT_CHAPTER = "这一章";
    private static final String DIALOGUE_INTENT = "他说了什么";

    /**
     * 构建查询对象（增强健壮性 + 语义清晰）
     */
    public RetrievalQuery build(String question, int currentChapter) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be null or blank");
        }

        RetrievalQuery.RetrievalQueryBuilder builder = RetrievalQuery.builder()
                .question(question.trim());

        parseChapterRange(builder, question, currentChapter);
        parseRole(builder, question);

        return builder.build();
    }

    private void parseChapterRange(RetrievalQuery.RetrievalQueryBuilder builder,
                                   String question,
                                   int currentChapter) {
        if (currentChapter <= 0) {
            builder.chapterFrom(null);
            builder.chapterTo(null);
            return;
        }

        if (question.contains(LAST_CHAPTER)) {
            int target = Math.max(1, currentChapter - 1);
            builder.chapterFrom(target);
            builder.chapterTo(target);
        }
        else if (question.contains(CURRENT_CHAPTER)) {
            builder.chapterFrom(currentChapter);
            builder.chapterTo(currentChapter);
        }
        else {
            builder.chapterFrom(null);
            builder.chapterTo(null);
        }
    }

    private void parseRole(RetrievalQuery.RetrievalQueryBuilder builder, String question) {
        if (question.contains(DIALOGUE_INTENT)) {
            builder.role("dialogue");
        } else {
            builder.role(null);
        }
    }
}
