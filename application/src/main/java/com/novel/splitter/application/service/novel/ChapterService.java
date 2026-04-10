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
     * 获取指定小说的所有章节，按序号升序
     *
     * @param novelId 小说ID
     * @return 章节列表
     */
    List<Chapter> getChaptersByNovelId(String novelId);

    boolean hasChapters(String novelId);

    void deleteByNovelId(String novelId);
}
