package com.novel.splitter.core;

import com.novel.splitter.domain.model.ParagraphType;
import com.novel.splitter.domain.model.RawParagraph;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 结构化段落切分器
 * <p>
 * 增强版的切分器，支持识别 Markdown 语法结构（标题、代码块、列表、引用）。
 * 并将代码块和标题标记为锚点（Anchor）。
 * </p>
 */
public class MarkdownParagraphSplitter extends ParagraphSplitter {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+.*");
    // 支持 - * + 以及 1. 2.
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*[-*+]|\\s*\\d+\\.)\\s+.*");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s+.*");
    private static final Pattern CODE_BLOCK_FENCE = Pattern.compile("^\\s*```.*");

    @Override
    public List<RawParagraph> split(List<String> rawLines) {
        List<RawParagraph> result = new ArrayList<>(rawLines.size());
        int index = 0;
        boolean inCodeBlock = false;

        for (String line : rawLines) {
            String trimmed = StringUtils.strip(line);
            boolean isEmpty = StringUtils.isEmpty(trimmed);
            String content = trimmed == null ? "" : trimmed;

            ParagraphType type = ParagraphType.TEXT;
            boolean isAnchor = false;

            // 1. 代码块判定
            if (CODE_BLOCK_FENCE.matcher(content).matches()) {
                inCodeBlock = !inCodeBlock;
                type = ParagraphType.CODE_BLOCK;
                isAnchor = true; // 围栏本身也是锚点
            } else if (inCodeBlock) {
                type = ParagraphType.CODE_BLOCK;
                isAnchor = true; // 代码块内部内容不可切分
                // 代码块内部建议保留一定的缩进，但这里为了兼容性暂且跟随 strip 策略，
                // 或者我们可以选择保留 line (但在 RawParagraph 中通常约定是 clean content)
                // 如果需要保留缩进，应该修改 content 取值逻辑。
                // 鉴于这是一个 "Novel Splitter"，我们暂时假设 "Technical Document" 也是文本流。
            } else if (!isEmpty) {
                // 2. 其他 Markdown 元素判定 (仅在非代码块内)
                if (HEADER_PATTERN.matcher(content).matches()) {
                    type = ParagraphType.HEADER;
                    isAnchor = true; // 标题不应被切断
                } else if (LIST_PATTERN.matcher(content).matches()) {
                    type = ParagraphType.LIST_ITEM;
                } else if (QUOTE_PATTERN.matcher(content).matches()) {
                    type = ParagraphType.QUOTE;
                }
            }

            RawParagraph paragraph = RawParagraph.builder()
                    .index(index++)
                    .content(content)
                    .isEmpty(isEmpty)
                    .type(type)
                    .isAnchor(isAnchor)
                    .build();

            result.add(paragraph);
        }

        return result;
    }
}
