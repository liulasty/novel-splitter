package com.novel.splitter.domain.model.dto;

import lombok.Data;

/**
 * 小说拆分请求参数
 */
@Data
public class SplitRequest {
    /** 
     * 待拆分的小说文件路径 
     */
    private String filePath;
    
    /** 
     * 处理版本标识 (默认 v1-rest) 
     */
    private String version = "v1-rest";
}
