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
 * 在 NLP 和 RAG（检索增强生成）系统中，用于对带 Markdown 格式的原始文本进行清洗和初步切分。
 * </p>
 */
public class MarkdownParagraphSplitter extends ParagraphSplitter {

    /**
     * 匹配 Markdown 标题的正则表达式（支持 1-6 级标题，如 "# 标题"）。
     */
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+.*");
    
    /**
     * 匹配 Markdown 列表项的正则表达式（支持 - * + 以及 1. 2. 等有序列表）。
     */
    private static final Pattern LIST_PATTERN = Pattern.compile("^(\\s*[-*+]|\\s*\\d+\\.)\\s+.*");
    
    /**
     * 匹配 Markdown 引用的正则表达式（如 "> 引用内容"）。
     */
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s+.*");
    
    /**
     * 匹配 Markdown 代码块围栏的正则表达式（如 "```java" 或 "```"）。
     */
    private static final Pattern CODE_BLOCK_FENCE = Pattern.compile("^\\s*```.*");

    /**
     * 将原始字符串行列表切分为结构化的原始段落列表。
     * <p>
     * 逐行遍历输入，识别其中的 Markdown 结构（如标题、代码块等），
     * 并为每一行构建一个带有类型和锚点标记的 {@link RawParagraph}。
     * </p>
     *
     * @param rawLines 原始文本行的列表
     * @return 切分并标记后的原始段落（{@link RawParagraph}）列表
     */
    @Override
    public List<RawParagraph> split(List<String> rawLines) {
        // 预分配结果列表的容量以提升性能
        List<RawParagraph> result = new ArrayList<>(rawLines.size());
        // 段落的全局索引
        int index = 0;
        // 标记当前解析状态是否处于代码块内部
        boolean inCodeBlock = false;

        for (String line : rawLines) {
            // 去除行首尾的空白字符
            String trimmed = StringUtils.strip(line);
            // 检查当前行是否为空行
            boolean isEmpty = StringUtils.isEmpty(trimmed);
            // 确保内容不为 null
            String content = trimmed == null ? "" : trimmed;

            if (NovelLineNoiseFilter.shouldSkipParagraphLine(content)) {
                continue;
            }

            // 默认段落类型为普通文本
            ParagraphType type = ParagraphType.TEXT;
            // 默认不是锚点
            boolean isAnchor = false;

            // 1. 代码块判定逻辑
            if (CODE_BLOCK_FENCE.matcher(content).matches()) {
                // 遇到代码块围栏标记（```），翻转状态
                inCodeBlock = !inCodeBlock;
                type = ParagraphType.CODE_BLOCK;
                isAnchor = true; // 围栏本身作为边界，也是锚点
            } else if (inCodeBlock) {
                // 如果处于代码块内部
                type = ParagraphType.CODE_BLOCK;
                isAnchor = true; // 代码块内部内容视为整体，不可切分
                // 代码块内部建议保留一定的缩进，但这里为了兼容性暂且跟随 strip 策略，
                // 或者我们可以选择保留 line (但在 RawParagraph 中通常约定是 clean content)
                // 如果需要保留缩进，应该修改 content 取值逻辑。
                // 鉴于这是一个 "Novel Splitter"，我们暂时假设 "Technical Document" 也是文本流。
            } else if (!isEmpty) {
                // 2. 其他 Markdown 元素判定 (仅在非代码块内进行判定)
                if (HEADER_PATTERN.matcher(content).matches()) {
                    type = ParagraphType.HEADER;
                    isAnchor = true; // 标题具有结构性意义，不应被切断
                } else if (LIST_PATTERN.matcher(content).matches()) {
                    type = ParagraphType.LIST_ITEM; // 列表项
                } else if (QUOTE_PATTERN.matcher(content).matches()) {
                    type = ParagraphType.QUOTE; // 引用
                }
            }

            // 构建带有解析信息的 RawParagraph 对象
            RawParagraph paragraph = RawParagraph.builder()
                    .index(index++) // 分配当前索引并自增
                    .content(content) // 设置段落内容
                    .isEmpty(isEmpty) // 标记是否为空段落
                    .type(type) // 设置段落类型
                    .isAnchor(isAnchor) // 标记是否为锚点
                    .build();

            result.add(paragraph);
        }

        return result;
    }
}
