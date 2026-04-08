package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 物理段落切分器
 * <p>
 * 负责将读取的文本行列表转换为结构化的 RawParagraph 对象列表。
 * 在此过程中会执行基础的文本清洗操作（例如去除首尾的空白字符），
 * 为后续的 NLP 处理和 RAG 知识库构建提供标准化的底层段落数据。
 * </p>
 */
public class ParagraphSplitter {

    /**
     * 执行段落切分与结构化转换操作。
     *
     * @param rawLines 原始文本行列表，通常来源于直接读取的文件内容
     * @return 经过基础清洗并转换后的结构化原始段落（RawParagraph）列表
     */
    public List<RawParagraph> split(List<String> rawLines) {
        // 初始化结果列表，预分配容量以优化性能
        List<RawParagraph> result = new ArrayList<>(rawLines.size());
        int index = 0; // 用于记录段落的全局索引位置

        // 遍历所有的原始文本行
        for (String line : rawLines) {
            // 使用 StringUtils.strip 去除字符串首尾的空白字符（包括 Unicode 空白字符）
            String trimmed = StringUtils.strip(line);

            // 使用建造者模式构建结构化的 RawParagraph 对象
            RawParagraph paragraph = RawParagraph.builder()
                    // 分配递增的索引值，用于追踪段落在全文中的物理位置
                    .index(index++)
                    // 如果清理后的字符串为 null，则替换为空字符串，防止后续处理出现空指针异常
                    .content(trimmed == null ? "" : trimmed)
                    // 判断清理后的字符串是否为空，标记段落的空状态（空段落后续可能会被过滤或合并）
                    .isEmpty(StringUtils.isEmpty(trimmed))
                    .build();

            // 将构建好的段落对象加入到结果列表中
            result.add(paragraph);
        }

        return result; // 返回完整的结构化段落列表
    }
}
