package com.novel.splitter.domain.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return switch (errorCode) {
            case UNAUTHORIZED, TOKEN_INVALID -> 401;
            case NOVEL_NOT_FOUND, TASK_NOT_FOUND, NOT_FOUND,
                 DLQ_MESSAGE_NOT_FOUND, CHROMA_COLLECTION_NOT_FOUND -> 404;
            default -> 400;
        };
    }
}
