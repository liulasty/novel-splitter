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
        // decorative lines (all dashes, equals, asterisks, etc.)
        if (content.codePoints().allMatch(cp ->
                cp == '-' || cp == '—' || cp == '－' || cp == '='
                        || cp == '＝' || cp == '~' || cp == '_' || cp == '*'
                        || cp == '·' || Character.isWhitespace(cp))) {
            return true;
        }
        // metadata / site banner lines
        String lower = content.toLowerCase();
        if (isMetadataLine(content, lower)) {
            return true;
        }
        return false;
    }

    private static boolean isMetadataLine(String content, String lower) {
        // author line
        if (content.startsWith("作者") || content.startsWith("作者：") || content.startsWith("作者:")) {
            return true;
        }
        // publish annotation: "发表于", "出品", "首发"
        if (lower.contains("发表于") || lower.contains("出品") || lower.contains("首发")) {
            return true;
        }
        // standalone publication site/domain (e.g. "sexinsex", "sis", "eyny")
        if (lower.matches(".*(sexinsex|sis|eyny|chinya|春满四合院|第一会所).*")) {
            return true;
        }
        // standalone date line (e.g. "2020/10/06", "2014-7-1")
        if (content.matches("^\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}$")) {
            return true;
        }
        // continuation marker (e.g. "（待续）")
        if (content.matches("^[（(].*[待续完结未终].*[）)]$") && content.length() < 20) {
            return true;
        }
        return false;
    }
}
