package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.Chapter;

import java.util.List;

public interface ChapterService {

    /**
     * 批量保存章节
     *
     * @param chapters 章节实体列表
     */
    void saveChapters(List<Chapter> chapters);

    /**
     * 原子整体替换小说的章节基准：清空旧 chapters + 写入新 chapters 在<strong>同一事务</strong>内完成，
     * 要么新基准全量落库，要么旧基准全量保留（用于强制重解析时避免出现半成品基准）。
     *
     * @param novelId  小说ID
     * @param chapters 新的完整章节集合（可为空，表示清空基准）
     */
    void replaceAll(String novelId, List<Chapter> chapters);

    /**
     * 获取指定小说的所有章节，按序号升序
     *
     * @param novelId 小说ID
     * @return 章节列表
     */
    List<Chapter> getChaptersByNovelId(String novelId);

    boolean hasChapters(String novelId);

    void deleteByNovelId(String novelId);
}
