package com.novel.splitter.pipeline.context;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

/**
 * 流水线上下文
 * <p>
 * 贯穿整个处理流程，存储配置信息、中间产物和最终结果。
 * </p>
 */
@Data
@Builder
public class PipelineContext {
    // === 输入 ===
    /** 小说名称（通常是文件名去掉后缀） */
    private String novelName;
    
    /** 源文件路径 */
    private Path sourceFile;
    
    /** 切分策略版本 (e.g., "v1-basic") */
    private String version;
    
    // === 中间产物 ===
    /** 原始文本行 */
    private List<String> rawLines;
    
    /** 物理段落列表 */
    private List<RawParagraph> paragraphs;
    
    /** 章节列表 */
    private List<Chapter> chapters;
    
    // === 最终产物 ===
    /** 生成的 Scene 列表 */
    private List<Scene> scenes;
}
