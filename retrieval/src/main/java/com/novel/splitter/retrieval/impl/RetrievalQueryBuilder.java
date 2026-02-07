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

    /**
     * 构建查询对象
     *
     * @param question       用户自然语言问题
     * @param currentChapter 当前阅读的章节号
     * @return 结构化的 RetrievalQuery
     */
    public RetrievalQuery build(String question, int currentChapter) {
        if (question == null) {
            throw new IllegalArgumentException("Question cannot be null");
        }

        RetrievalQuery.RetrievalQueryBuilder builder = RetrievalQuery.builder()
                .question(question);

        // 1. 解析章节范围 (Chapter Range)
        parseChapterRange(builder, question, currentChapter);

        // 2. 解析角色/功能 (Role)
        parseRole(builder, question);

        return builder.build();
    }

    private void parseChapterRange(RetrievalQuery.RetrievalQueryBuilder builder, String question, int currentChapter) {
        if (question.contains("上一章")) {
            // “上一章” → chapterFrom = current - 1
            // 注意：如果当前是第1章，上一章可能是0或者无，这里简单处理为 current - 1
            int target = Math.max(0, currentChapter - 1);
            builder.chapterFrom(target);
            builder.chapterTo(target); // 通常指“那一章”，所以是点查
        } else if (question.contains("这一章")) {
            // “这一章” → chapterFrom = chapterTo = current
            builder.chapterFrom(currentChapter);
            builder.chapterTo(currentChapter);
        } else {
            // 无法确定范围时，必须显式设置为 null
            builder.chapterFrom(null);
            builder.chapterTo(null);
        }
    }

    private void parseRole(RetrievalQuery.RetrievalQueryBuilder builder, String question) {
        if (question.contains("他说了什么")) {
            // “他说了什么” → role = dialogue
            builder.role("dialogue");
        } else {
            builder.role(null);
        }
    }
}
