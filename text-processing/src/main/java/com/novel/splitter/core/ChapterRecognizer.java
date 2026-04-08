package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 章节识别器
 * <p>
 * 基于正则表达式识别章节标题，并将连续的段落归组为 Chapter 对象。
 * 适用于 NLP 和 RAG（检索增强生成）系统，用于对长篇小说进行结构化分章，便于后续语料处理。
 * </p>
 */
public class ChapterRecognizer {

    /**
     * 匹配章节标题的正则表达式模式。
     * <p>
     * 匹配 "第1章", "第一章", "第100回", "Chapter 1" 等常见格式。
     * 宽松模式：允许前面有空白字符，允许后面有额外的标题内容。
     * </p>
     */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*第[0-9零一二三四五六七八九十百千两]+[章回节卷].*|^\\s*Chapter\\s*\\d+.*");

    /**
     * 标题最大长度限制。
     * <p>
     * 用于防止将过长的普通句子误判为章节标题，只有长度不超过此值的段落才会参与匹配。
     * </p>
     */
    private static final int MAX_TITLE_LENGTH = 50;

    /**
     * 根据段落列表识别并构建章节结构。
     *
     * @param paragraphs 原始段落列表，即从文本中拆分出的所有独立段落
     * @return 识别并构建完成的章节（{@link Chapter}）列表
     */
    public List<Chapter> recognize(List<RawParagraph> paragraphs) {
        // 用于存储识别出的所有章节结果
        List<Chapter> chapters = new ArrayList<>();
        // 章节编号，从 1 开始递增
        int chapterIndex = 1;

        // 记录当前章节的起始段落索引
        int currentStart = 0;
        // 当前章节的标题，默认将第一章之前的文本归为“序章/前言”
        String currentTitle = "序章/前言";

        // 遍历所有段落进行章节标题匹配
        for (int i = 0; i < paragraphs.size(); i++) {
            RawParagraph p = paragraphs.get(i);

            // 检查当前段落是否符合章节标题的格式
            if (isChapterTitle(p)) {
                // 发现新章节标题，需要结算（保存）上一章节的内容
                // 只有当这一章包含实际段落内容（i > 0）时才进行结算
                if (i > 0) {
                    chapters.add(Chapter.builder()
                            .index(chapterIndex++) // 设置当前章节编号并自增
                            .title(currentTitle) // 设置上一章的标题
                            .startParagraphIndex(currentStart) // 设置上一章的起始段落索引
                            .endParagraphIndex(i - 1) // 设置上一章的结束段落索引（当前标题段落的前一段）
                            .build());
                }

                // 开启新的一章，更新起始索引和标题内容
                currentStart = i;
                currentTitle = p.getContent();
            }
        }

        // 遍历结束后，结算最后剩余的段落作为一个独立章节
        if (currentStart < paragraphs.size()) {
            chapters.add(Chapter.builder()
                    .index(chapterIndex) // 设置最后一个章节的编号
                    .title(currentTitle) // 设置最后一个章节的标题
                    .startParagraphIndex(currentStart) // 起始段落索引
                    .endParagraphIndex(paragraphs.size() - 1) // 结束段落索引为全文最后一个段落
                    .build());
        }

        return chapters;
    }

    /**
     * 判断指定段落是否为章节标题。
     * <p>
     * 通过检查段落是否为空、长度是否超过限制以及是否匹配预定义的章节标题正则表达式来进行综合判定。
     * </p>
     *
     * @param p 需要判断的原始段落对象
     * @return 如果段落被识别为章节标题，则返回 true；否则返回 false
     */
    private boolean isChapterTitle(RawParagraph p) {
        // 空段落不能作为章节标题
        if (p.isEmpty()) {
            return false;
        }
        String content = p.getContent();
        // 长度超过最大限制的段落被排除，以防误判长句
        if (content.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        // 使用预编译的正则表达式匹配段落内容
        return CHAPTER_PATTERN.matcher(content).matches();
    }
}
