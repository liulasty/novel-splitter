package com.novel.splitter.infrastructure.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件操作工具类
 * <p>
 * 提供稳健的文件读取功能，特别是针对中文小说常见的编码问题（UTF-8 vs GBK）。
 * </p>
 */
public class FileUtils {

    /**
     * 读取文本文件所有行，自动尝试检测编码。
     * 策略：优先使用 UTF-8 读取，如果抛出 MalformedInputException，则回退到 GB18030 (兼容 GBK/GB2312)。
     *
     * @param path 文件路径
     * @return 行列表
     * @throws IOException 如果文件不存在或无法读取
     */
    public static List<String> readLinesAutoDetectEncoding(Path path) throws IOException {
        try {
            // 优先尝试标准 UTF-8
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 如果 UTF-8 读取失败（通常是编码错误），尝试 GB18030
            // GB18030 是 GBK 的超集，兼容性更好
            try {
                return Files.readAllLines(path, Charset.forName("GB18030"));
            } catch (IOException ex) {
                // 如果 GBK 也失败，抛出原始异常或新异常
                throw new IOException("Failed to read file with UTF-8 and GB18030 encodings: " + path, ex);
            }
        }
    }
}
