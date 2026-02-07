package com.novel.splitter.domain.model.dto;

import lombok.Data;

/**
 * 小说下载请求参数
 */
@Data
public class DownloadRequest {
    /** 
     * 小说目录页或详情页 URL 
     */
    private String url;
    
    /** 
     * 小说名称 (用于保存文件名) 
     */
    private String name;
}
