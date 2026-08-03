package com.novel.splitter.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * File storage port.
 *
 * Application layer speaks in storage-root relative paths; adapters decide how/where to persist.
 */
public interface FileStoragePort {
    void write(String relativePath, InputStream content, boolean replaceExisting) throws IOException;

    boolean exists(String relativePath) throws IOException;

    Path toAbsolutePath(String relativePath) throws IOException;

    /**
     * Normalize an absolute or relative path into a storage-root relative path using '/' separators.
     * Implementations should reject paths that point outside the storage root.
     */
    String toRelativePath(String absoluteOrRelativePath) throws IOException;

    void deleteIfExists(String relativePath) throws IOException;

    void deleteTreeIfExists(String relativeDir) throws IOException;

    List<String> listFiles(String relativeDir) throws IOException;

    List<String> listDirectories(String relativeDir) throws IOException;
}

