package com.novel.splitter.pipeline.etl;

public class IngestException extends RuntimeException {

    private final boolean retryable;

    public IngestException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
