package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 物理段落切分器
 * <p>
 * 负责将读取的文本行列表转换为结构化的 RawParagraph 对象列表。
 * 同时执行基础清洗（去除首尾空白）。
 * </p>
 */
public class ParagraphSplitter {

    /**
     * 执行切分
     *
     * @param rawLines 原始文本行
     * @return 结构化的段落列表
     */
    public List<RawParagraph> split(List<String> rawLines) {
        List<RawParagraph> result = new ArrayList<>(rawLines.size());
        int index = 0;

        for (String line : rawLines) {
            // 使用 StringUtils.strip 去除 Unicode 空白字符
            String trimmed = StringUtils.strip(line);

            RawParagraph paragraph = RawParagraph.builder()
                    .index(index++)
                    .content(trimmed == null ? "" : trimmed)
                    .isEmpty(StringUtils.isEmpty(trimmed))
                    .build();

            result.add(paragraph);
        }

        return result;
    }
}
