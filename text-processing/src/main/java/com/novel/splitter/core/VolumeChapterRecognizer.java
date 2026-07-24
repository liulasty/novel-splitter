package com.novel.splitter.core;

import java.util.regex.Pattern;

/**
 * 卷章混合识别器：自动检测 "卷：" 卷头并拼接全局唯一章节标题。
 * <p>
 * 默认识别两种卷头格式：
 * <ol>
 *   <li>{@code 卷[：:]...} — 冒号分隔卷名（如"卷：阿里布达年代祭 第五十三集"）</li>
 *   <li>{@code 第N[集卷]...} — 第X集/第X卷（如"第五十三集"）</li>
 * </ol>
 * 章节行匹配复用 {@link ChapterRecognizer#DEFAULT_CHAPTER_PATTERN}（或自定义 Pattern）。
 * 装饰性分割线（{@code ====}等）会与卷头关联处理：近距离卷头被采纳。
 */
public class VolumeChapterRecognizer {

    /** 默认卷头正则 */
    private static final Pattern DEFAULT_VOLUME_PATTERN = Pattern.compile(
            "^(?:[\\s　]*[-=*_]{4,}[\\s　]*)?"
            + "(?:[\\s　]*卷[：:][\\s　]*|"
            + "第[0-9\\uFF10-\\uFF19零一二三四五六七八九十百千两]+[集卷])"
            + ".*$");

    private final Pattern chapterPattern;
    private final Pattern volumePattern;

    public VolumeChapterRecognizer() {
        this(ChapterRecognizer.defaultChapterPattern(), DEFAULT_VOLUME_PATTERN);
    }

    public VolumeChapterRecognizer(Pattern chapterPattern, Pattern volumePattern) {
        this.chapterPattern = chapterPattern != null ? chapterPattern : ChapterRecognizer.defaultChapterPattern();
        this.volumePattern = volumePattern != null ? volumePattern : DEFAULT_VOLUME_PATTERN;
    }

    public Pattern getChapterPattern() {
        return chapterPattern;
    }

    /**
     * 编译用户自定义卷头正则。
     */
    public static Pattern compileUserVolumePattern(String regex) {
        if (regex == null || regex.isBlank()) {
            return DEFAULT_VOLUME_PATTERN;
        }
        return Pattern.compile(regex.trim());
    }

    /**
     * 判断是否卷标题行。
     */
    public boolean isVolumeTitleLine(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String trimmed = ChapterRecognizer.stripLeadingUtf8Bom(content.trim());
        if (trimmed.length() > 60) {
            return false;
        }
        return volumePattern.matcher(trimmed).matches();
    }

    /**
     * 从卷标题行提取卷名（用于拼接章节标题）。
     * 输入如 "卷：阿里布达年代祭 第五十三集"，提取 "第五十三集" 或原始文本。
     */
    public String extractVolumeName(String volumeTitleLine) {
        if (volumeTitleLine == null || volumeTitleLine.isEmpty()) {
            return "";
        }
        String trimmed = ChapterRecognizer.stripLeadingUtf8Bom(volumeTitleLine.trim());
        // 去除包裹的装饰线
        trimmed = trimmed.replaceAll("^[-=*_]+", "").replaceAll("[-=*_]+$", "").trim();
        // 去除 "卷：" 或 "卷:" 前缀
        trimmed = trimmed.replaceFirst("^[\\s　]*卷[：:][\\s　]*", "");
        return trimmed.trim();
    }

    /**
     * 判断是否章节标题行（复用 ChapterRecognizer 逻辑）。
     */
    public boolean isChapterTitleLine(String content) {
        return ChapterRecognizer.isChapterTitleLine(content, chapterPattern);
    }

    /**
     * 构建全局唯一章节标题：卷名 + "-" + 章节标题。
     */
    public String buildFullChapterTitle(String volumeName, String chapterTitleLine) {
        if (volumeName == null || volumeName.isEmpty()) {
            return chapterTitleLine;
        }
        String cleanChapter = ChapterRecognizer.stripLeadingUtf8Bom(chapterTitleLine.trim());
        return volumeName + "-" + cleanChapter;
    }
}
