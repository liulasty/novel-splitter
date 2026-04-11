package com.novel.splitter.application.model.command;

import java.io.InputStream;

public record UploadNovelCommand(
        InputStream content,
        String originalFilename,
        String title,
        String author,
        String description,
        /** multipart 大小，未知时 -1 */
        long sizeBytes
) {
}

