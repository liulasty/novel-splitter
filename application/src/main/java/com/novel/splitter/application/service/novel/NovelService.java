package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.entity.JpaNovelEntity;
import com.novel.splitter.domain.enums.NovelStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface NovelService {

    /**
     * 上传小说并记录到数据库
     *
     * @param file        上传的文件
     * @param title       小说标题
     * @param author      小说作者
     * @param description 描述
     * @return 返回 novelId
     */
    String createNovel(MultipartFile file, String title, String author, String description) throws IOException;

    /**
     * 更新小说状态机
     * 状态流转：PENDING -> SPLITTING -> SPLIT_COMPLETED -> EMBEDDING -> COMPLETED
     *
     * @param novelId 小说ID
     * @param status  新状态
     */
    void updateNovelStatus(String novelId, NovelStatus status);

    /**
     * 获取小说实体
     *
     * @param novelId 小说ID
     * @return 小说实体
     */
    JpaNovelEntity getNovelById(String novelId);

    /**
     * 获取所有小说
     */
    List<JpaNovelEntity> listNovels();
}
