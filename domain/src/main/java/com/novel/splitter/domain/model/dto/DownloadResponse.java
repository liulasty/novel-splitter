package com.novel.splitter.domain.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说下载响应结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResponse {
    /** 
     * 任务状态 (Success/Fail) 
     */
    private String status;
    
    /** 
     * 小说文件保存的绝对路径 
     */
    private String savedPath;
}
