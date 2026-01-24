package com.novel.splitter.assembler;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.assembler.impl.DefaultContextAssembler;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerTest {

    private DefaultContextAssembler assembler;
    private AssemblerConfig config;

    @BeforeEach
    void setUp() {
        config = new AssemblerConfig();
        config.setMaxChunks(3);
        config.setMaxChunkLength(10); // 测试截断
        assembler = new DefaultContextAssembler(config);
    }

    @Test
    void testEmptyInput() {
        String result = assembler.assemble(Collections.emptyList());
        assertTrue(result.contains("【Context Empty】"));
    }

    @Test
    void testSorting() {
        // 创建乱序 Chunk
        Scene c1 = createScene("C1", 2, 10);
        Scene c2 = createScene("C2", 1, 5); // 应该排第一
        Scene c3 = createScene("C3", 1, 20); // 应该排第二

        String context = assembler.assemble(Arrays.asList(c1, c2, c3));
        
        // 验证顺序：C2 -> C3 -> C1
        // 注意：输出包含 Header，我们要检查内容出现的顺序
        int idx1 = context.indexOf("章节：Chapter 1");
        int idx2 = context.indexOf("段落：5");
        int idx3 = context.indexOf("段落：20");
        int idx4 = context.indexOf("章节：Chapter 2");

        assertTrue(idx1 < idx4, "Chapter 1 should be before Chapter 2");
        assertTrue(idx2 < idx3, "Paragraph 5 should be before Paragraph 20");
    }

    @Test
    void testTruncation() {
        // 内容长度 20，限制 10
        Scene c1 = createScene("12345678901234567890", 1, 1);
        String context = assembler.assemble(Collections.singletonList(c1));

        // 截取中间：5-15 -> "6789012345"
        // 由于添加了省略号，实际长度是 3 + 10 + 3 = 16
        // 且内容被省略号包裹
        assertTrue(context.contains("...6789012345..."), "Content should be truncated from middle with ellipsis");
        // 注意：truncation logic:
        // content="12345678901234567890", limit=10
        // start = (20 - 10) / 2 = 5
        // substring(5, 15) -> "6789012345"
        // result = "...6789012345..."
        
        // "12345" 字符串本身确实作为子串出现在了 "6789012345" 中（"12345" 在尾部出现）
        // 等等，substring(5, 15) 是从 index 5 开始取 10 个字符。
        // Index: 01234567890123456789
        // Char:  12345678901234567890
        // Index 5 is '6'.
        // Substring is "6789012345".
        // does "6789012345" contain "12345"? YES! At the end.
        // 所以原测试断言 assertTrue(!context.contains("12345")) 是错误的，因为它碰巧包含了 "12345"。
        
        // 应该断言它不包含原始字符串的 *开头部分*，比如 "123456" 或者更准确地说，不包含 index 0-4 的内容 "12345" 作为 *前缀*
        // 但由于 content 只是 String，我们只能检查是否包含特定片段。
        // "12345" 在截断后的字符串里确实存在。
        // 让我们换一个测试用例，避免这种数字重叠的混淆。
        
        // 调试输出
        System.out.println("Truncated context: " + context);
        
        // 修改断言：检查是否包含完整原文
        assertTrue(!context.contains("12345678901234567890"), "Should not contain full original text");
    }
    
    @Test
    void testMaxChunksLimit() {
        config.setMaxChunks(2);
        Scene c1 = createScene("C1", 1, 1);
        Scene c2 = createScene("C2", 1, 2);
        Scene c3 = createScene("C3", 1, 3);
        
        String context = assembler.assemble(Arrays.asList(c1, c2, c3));
        
        // 应该只有前两个
        assertTrue(context.contains("段落：1"));
        assertTrue(context.contains("段落：2"));
        assertTrue(!context.contains("段落：3"));
    }

    private Scene createScene(String text, int chapterIndex, int startPara) {
        return Scene.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .chapterTitle("Chapter " + chapterIndex)
                .chapterIndex(chapterIndex)
                .startParagraphIndex(startPara)
                .endParagraphIndex(startPara + 5)
                .metadata(SceneMetadata.builder()
                        .novel("Test Novel")
                        .chapterTitle("Chapter " + chapterIndex)
                        .chapterIndex(chapterIndex)
                        .startParagraph(startPara)
                        .endParagraph(startPara + 5)
                        .role("narration")
                        .build())
                .build();
    }
}
