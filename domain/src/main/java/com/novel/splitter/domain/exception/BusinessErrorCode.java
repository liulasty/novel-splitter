package com.novel.splitter.domain.exception;

public enum BusinessErrorCode {

    // 通用错误 (1000–1999)
    INTERNAL_ERROR(1000, "服务器繁忙，请稍后再试"),
    SERVICE_UNAVAILABLE(1001, "服务暂不可用"),
    BAD_REQUEST(1002, "请求参数错误"),
    VALIDATION_ERROR(1003, "参数校验失败"),
    NOT_FOUND(1004, "资源不存在"),

    // 小说 (2000–2999)
    NOVEL_NOT_FOUND(2000, "小说不存在"),
    NOVEL_UPLOAD_CONTENT_REQUIRED(2001, "上传内容不能为空"),
    NOVEL_ONLY_TXT(2002, "仅支持 .txt 文件"),
    NOVEL_FILE_EMPTY(2003, "文件为空"),
    NOVEL_FILE_TOO_LARGE(2004, "文件过大"),
    NOVEL_DOWNLOAD_FAILED(2005, "下载失败"),

    // 任务 (3000–3999)
    TASK_NOT_FOUND(3000, "任务不存在"),
    TASK_INVALID_STAGE(3001, "不支持的处理阶段"),
    TASK_INVALID_STATE(3002, "任务状态错误"),
    TASK_FAILED(3003, "任务处理失败"),

    // RAG / QA 问答 (4000–4999)
    RAG_QUERY_FAILED(4000, "RAG 查询失败"),
    RAG_EMPTY_RESULTS(4001, "未检索到相关内容"),
    RAG_LLM_UNAVAILABLE(4002, "LLM 服务不可用"),

    // Chroma / 向量 (5000–5999)
    CHROMA_CONNECTION_FAILED(5000, "向量数据库连接失败"),
    CHROMA_QUERY_FAILED(5001, "向量查询失败"),
    CHROMA_COLLECTION_NOT_FOUND(5002, "集合不存在"),

    // DLQ 死信 (6000–6999)
    DLQ_MESSAGE_NOT_FOUND(6000, "死信消息不存在"),
    DLQ_REQUEUE_FAILED(6001, "消息重新投递失败"),

    // 鉴权 (7000–7999)
    UNAUTHORIZED(7000, "未授权访问"),
    TOKEN_INVALID(7001, "Token 无效或已过期"),
    ;

    private final int code;
    private final String defaultMessage;

    BusinessErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
