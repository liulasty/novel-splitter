package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.EmbedStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景 (Scene)
 * <p>
 * 系统的核心产出物，代表一个相对独立的、语义完整的文本块。
 * 通常包含 500~2000 字。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scene {
    /**
     * 数据库主键（向量化等批量任务使用）；与 {@link #id}（业务 sceneId）不同。
     */
    private Long persistenceId;

    /**
     * 唯一标识 (UUID 或 Hash)
     */
    private String id;

    /**
     * 所属章节标题
     */
    private String chapterTitle;

    /**
     * 所属章节索引
     */
    private int chapterIndex;

    /**
     * 起始段落索引（全局 RawParagraph index）
     */
    private int startParagraphIndex;

    /**
     * 结束段落索引（全局 RawParagraph index）
     */
    private int endParagraphIndex;

    /**
     * 完整文本内容
     */
    private String text;

    /**
     * 字数
     */
    private int wordCount;
    
    /**
     * 前文摘要/上下文 (Overlap Context)
     * 保留上一场景的最后 100-200 字，用于维持连贯性
     */
    private String prefixContext;

    /**
     * 是否可再切分
     * <p>如果是长文本，建议为 true；如果已经很短，为 false。</p>
     */
    private boolean canSplit;

    /**
     * 元数据
     */
    private SceneMetadata metadata;

    /**
     * 全局场景序号（(novelId, version) 内单调递增），幂等落库与续传的落点。
     * 对应 DB scenes.seq 列；为 null 时由切分/落库阶段分配。
     */
    private Long seq;

    /**
     * 检索评分 (非持久化字段，运行时注入)
     */
    private Double score;

    private EmbedStatus embedStatus;
    private String embedError;
    /** 最近一次写入该行的向量化运行（用于审计 / 续传过滤）。 */
    private String embedRunId;
}
