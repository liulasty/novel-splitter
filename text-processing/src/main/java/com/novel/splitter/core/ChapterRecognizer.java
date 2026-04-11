package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 章节识别器：基于正则识别章节标题行。可使用 {@link #defaultChapterPattern()} 或自定义整行匹配正则。
 */
public class ChapterRecognizer {

    /**
     * 默认：匹配 "第1章"、"第一章"、"第100回"、"Chapter 1" 等。
     */
    /** 含 ASCII 数字与全角数字（０-９），避免「第１章」等无法匹配 */
    public static final Pattern DEFAULT_CHAPTER_PATTERN = Pattern.compile(
            "^\\s*第\\s*[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+\\s*[章回节卷].*|^\\s*Chapter\\s*\\d+.*");

    private static final Pattern DECORATIVE_RULE_LINE = Pattern.compile("^[-=*_]{4,}$");
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MIN_TOC_CHAPTER_LINES = 2;

    private final Pattern chapterPattern;

    public ChapterRecognizer() {
        this.chapterPattern = DEFAULT_CHAPTER_PATTERN;
    }

    public ChapterRecognizer(Pattern chapterPattern) {
        this.chapterPattern = chapterPattern != null ? chapterPattern : DEFAULT_CHAPTER_PATTERN;
    }

    /**
     * @throws PatternSyntaxException 正则非法时抛出，由调用方转为 400
     */
    public static Pattern compileUserPattern(String regex) {
        if (regex == null || regex.isBlank()) {
            return DEFAULT_CHAPTER_PATTERN;
        }
        return Pattern.compile(regex.trim());
    }

    public static Pattern defaultChapterPattern() {
        return DEFAULT_CHAPTER_PATTERN;
    }

    public Pattern getChapterPattern() {
        return chapterPattern;
    }

    public static int skipLeadingTableOfContents(List<String> lines) {
        return skipLeadingTableOfContents(lines, DEFAULT_CHAPTER_PATTERN);
    }

    /**
     * 与 {@link #isChapterTitleLine(String, Pattern)} 使用同一 pattern，保证目录跳过与正文分章一致。
     */
    public static int skipLeadingTableOfContents(List<String> lines, Pattern chapterPattern) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        Pattern p = chapterPattern != null ? chapterPattern : DEFAULT_CHAPTER_PATTERN;
        int i = 0;
        int n = lines.size();

        while (i < n && isBlankLine(lines.get(i))) {
            i++;
        }

        if (i < n && isDecorativeRuleLine(lines.get(i))) {
            i++;
            while (i < n && !isDecorativeRuleLine(lines.get(i))) {
                i++;
            }
            if (i < n && isDecorativeRuleLine(lines.get(i))) {
                i++;
            }
            while (i < n && isBlankLine(lines.get(i))) {
                i++;
            }
        }

        int tocRun = countLeadingChapterTitleRun(lines, i, p);
        if (tocRun >= MIN_TOC_CHAPTER_LINES) {
            i += tocRun;
            while (i < n && isBlankLine(lines.get(i))) {
                i++;
            }
        }

        return Math.min(i, n);
    }

    private static int countLeadingChapterTitleRun(List<String> lines, int start, Pattern pattern) {
        int n = lines.size();
        int j = start;
        int count = 0;
        while (j < n) {
            String t = lines.get(j);
            if (isBlankLine(t)) {
                j++;
                continue;
            }
            if (isChapterTitleLine(t, pattern)) {
                count++;
                j++;
            } else {
                break;
            }
        }
        return count;
    }

    private static boolean isBlankLine(String line) {
        return line == null || line.trim().isEmpty();
    }

    private static boolean isDecorativeRuleLine(String line) {
        if (line == null) {
            return false;
        }
        return DECORATIVE_RULE_LINE.matcher(line.trim()).matches();
    }

    public boolean isLikelyChapterTitle(String content) {
        return isChapterTitleLine(content, chapterPattern);
    }

    /**
     * 是否与章节标题行一致（与分章规则对齐）。
     */
    public static boolean isChapterTitleLine(String content, Pattern pattern) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String trimmed = stripLeadingUtf8Bom(content.trim());
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        Pattern p = pattern != null ? pattern : DEFAULT_CHAPTER_PATTERN;
        return p.matcher(trimmed).matches();
    }

    /**
     * UTF-8 BOM（\uFEFF）在章节标题行首时，{@link String#trim()} 无法去掉，会导致整行匹配失败。
     */
    public static String stripLeadingUtf8Bom(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        String t = s;
        while (t.startsWith("\uFEFF")) {
            t = t.substring(1);
        }
        return t;
    }

    public List<Chapter> recognize(List<RawParagraph> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> contents = new ArrayList<>(paragraphs.size());
        for (RawParagraph p : paragraphs) {
            contents.add(p.getContent() != null ? p.getContent() : "");
        }
        int skip = skipLeadingTableOfContents(contents, chapterPattern);
        List<RawParagraph> body = skip <= 0
                ? paragraphs
                : new ArrayList<>(paragraphs.subList(skip, paragraphs.size()));
        if (body.isEmpty()) {
            return new ArrayList<>();
        }

        List<Chapter> chapters = new ArrayList<>();
        int chapterIndex = 1;
        int currentBodyStart = 0;
        String currentTitle = "序章/前言";

        for (int i = 0; i < body.size(); i++) {
            RawParagraph p = body.get(i);

            if (isChapterTitle(p)) {
                if (i > 0) {
                    chapters.add(Chapter.builder()
                            .index(chapterIndex++)
                            .title(currentTitle)
                            .startParagraphIndex(body.get(currentBodyStart).getIndex())
                            .endParagraphIndex(body.get(i - 1).getIndex())
                            .build());
                }

                currentBodyStart = i;
                currentTitle = p.getContent();
            }
        }

        if (currentBodyStart < body.size()) {
            chapters.add(Chapter.builder()
                    .index(chapterIndex)
                    .title(currentTitle)
                    .startParagraphIndex(body.get(currentBodyStart).getIndex())
                    .endParagraphIndex(body.get(body.size() - 1).getIndex())
                    .build());
        }

        return chapters;
    }

    private boolean isChapterTitle(RawParagraph p) {
        if (p.isEmpty()) {
            return false;
        }
        return isChapterTitleLine(p.getContent(), chapterPattern);
    }
}
