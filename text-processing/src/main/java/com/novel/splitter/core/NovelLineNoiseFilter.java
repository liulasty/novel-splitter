package com.novel.splitter.core;

/**
 * 在段落索引前剔除爬取 TXT 中常见的低价值行（目录头、分隔规则线等）。
 */
public final class NovelLineNoiseFilter {

    private NovelLineNoiseFilter() {
    }

    /**
     * @param content 已 trim 的行（非 null）
     * @return 若该行不应成为 {@link com.novel.splitter.domain.model.RawParagraph}，则返回 true
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
        // 装饰性分隔线（全为横线、等号、星号等）
        if (content.codePoints().allMatch(cp ->
                cp == '-' || cp == '—' || cp == '－' || cp == '='
                        || cp == '＝' || cp == '~' || cp == '_' || cp == '*'
                        || cp == '·' || Character.isWhitespace(cp))) {
            return true;
        }
        // 元数据 / 站点横幅行
        String lower = content.toLowerCase();
        if (isMetadataLine(content, lower)) {
            return true;
        }
        return false;
    }

    private static boolean isMetadataLine(String content, String lower) {
        // 作者行
        if (content.startsWith("作者") || content.startsWith("作者：") || content.startsWith("作者:")) {
            return true;
        }
        // 发布标注："发表于"、"出品"、"首发"
        if (lower.contains("发表于") || lower.contains("出品") || lower.contains("首发")) {
            return true;
        }
        // 独立的发布站点/域名（如 "sexinsex"、"sis"、"eyny"）
        if (lower.matches(".*(sexinsex|sis|eyny|chinya|春满四合院|第一会所).*")) {
            return true;
        }
        // 独立的日期行（如 "2020/10/06"、"2014-7-1"）
        if (content.matches("^\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}$")) {
            return true;
        }
        // 连载未完标记（如 "（待续）"）
        if (content.matches("^[（(].*[待续完结未终].*[）)]$") && content.length() < 20) {
            return true;
        }
        return false;
    }
}
