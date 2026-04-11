package com.novel.splitter.core;

/**
 * Drops low-signal lines common in crawled TXT (TOC headers, separator rules) before paragraph indexing.
 */
public final class NovelLineNoiseFilter {

    private NovelLineNoiseFilter() {
    }

    /**
     * @param content trimmed line (non-null)
     * @return true if this line should not become a {@link com.novel.splitter.domain.model.RawParagraph}
     */
    public static boolean shouldSkipParagraphLine(String content) {
        if (content.isEmpty()) {
            return false;
        }
        if ("章节目录".equals(content)) {
            return true;
        }
        if (content.length() < 3) {
            return false;
        }
        return content.codePoints().allMatch(cp ->
                cp == '-' || cp == '—' || cp == '－' || cp == '='
                        || cp == '＝' || cp == '~' || cp == '_' || cp == '*'
                        || cp == '·' || Character.isWhitespace(cp));
    }
}
