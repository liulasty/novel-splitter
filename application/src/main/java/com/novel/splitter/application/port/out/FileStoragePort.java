package com.novel.splitter.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件存储端口。
 *
 * 应用层使用相对存储根目录的路径通信；由各适配器决定如何/何处持久化。
 */
public interface FileStoragePort {
    void write(String relativePath, InputStream content, boolean replaceExisting) throws IOException;

    boolean exists(String relativePath) throws IOException;

    Path toAbsolutePath(String relativePath) throws IOException;

    /**
     * 将绝对或相对路径归一化为使用 '/' 分隔符的、相对存储根目录的路径。
     * 实现应拒绝指向存储根目录之外的路径。
     */
    String toRelativePath(String absoluteOrRelativePath) throws IOException;

    void deleteIfExists(String relativePath) throws IOException;

    void deleteTreeIfExists(String relativeDir) throws IOException;

    List<String> listFiles(String relativeDir) throws IOException;

    List<String> listDirectories(String relativeDir) throws IOException;
}

