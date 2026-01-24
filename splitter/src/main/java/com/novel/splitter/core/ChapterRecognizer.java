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
 * </p>
 */
public class ChapterRecognizer {

    // 匹配 "第1章", "第一章", "第100回", "Chapter 1" 等常见格式
    // 宽松模式：允许前面有空白，允许后面有标题内容
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*第[0-9零一二三四五六七八九十百千两]+[章回节卷].*|^\\s*Chapter\\s*\\d+.*");

    // 标题最大长度限制，防止将长句误判为标题
    private static final int MAX_TITLE_LENGTH = 50;

    /**
     * 识别章节结构
     *
     * @param paragraphs 所有段落
     * @return 章节列表
     */
    public List<Chapter> recognize(List<RawParagraph> paragraphs) {
        List<Chapter> chapters = new ArrayList<>();
        int chapterIndex = 1;

        int currentStart = 0;
        String currentTitle = "序章/前言"; // 默认第一章之前的文本归为序章

        for (int i = 0; i < paragraphs.size(); i++) {
            RawParagraph p = paragraphs.get(i);

            if (isChapterTitle(p)) {
                // 发现新章节标题，结算上一章
                // 只有当这一章有内容（i > currentStart）或者是第一章（i > 0）时才结算
                if (i > 0) {
                    chapters.add(Chapter.builder()
                            .index(chapterIndex++)
                            .title(currentTitle)
                            .startParagraphIndex(currentStart)
                            .endParagraphIndex(i - 1)
                            .build());
                }

                // 开启新的一章
                currentStart = i;
                currentTitle = p.getContent();
            }
        }

        // 结算最后一章
        if (currentStart < paragraphs.size()) {
            chapters.add(Chapter.builder()
                    .index(chapterIndex)
                    .title(currentTitle)
                    .startParagraphIndex(currentStart)
                    .endParagraphIndex(paragraphs.size() - 1)
                    .build());
        }

        return chapters;
    }

    /**
     * 判断某段落是否为章节标题
     */
    private boolean isChapterTitle(RawParagraph p) {
        if (p.isEmpty()) {
            return false;
        }
        String content = p.getContent();
        if (content.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        return CHAPTER_PATTERN.matcher(content).matches();
    }
}
