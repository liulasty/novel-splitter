package com.novel.splitter.embedding.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 管理 ONNX 模型生命周期和会话的组件。
 * 负责加载外部或内置的 Embedding 模型文件，并提供模型执行所需的环境和会话资源。
 */
@Slf4j
@Component
public class OnnxModelHolder {

    /** ONNX 运行时环境 */
    private OrtEnvironment env;
    /** ONNX 模型执行会话 */
    private OrtSession session;
    /** 启动时提取内置模型使用的临时目录（若使用外部模型则为空） */
    private Path extractedModelTempDir;
    
    /** 外部 ONNX 模型的文件路径，如果未配置则使用类路径下的内置模型 */
    @org.springframework.beans.factory.annotation.Value("${embedding.onnx.model-path:}")
    private String externalModelPath;

    /** ONNX 模型的执行提供者，例如 CPUExecutionProvider 或 CUDAExecutionProvider */
    @org.springframework.beans.factory.annotation.Value("${embedding.onnx.provider:CPUExecutionProvider}")
    private String provider;

    /** 内置模型所在的资源目录 */
    private static final String MODEL_RESOURCE_DIR = "embedding/";
    /** 内置 ONNX 模型文件名 */
    private static final String MODEL_FILE = "model.onnx";
    /** 内置 ONNX 模型附加数据文件名 */
    private static final String MODEL_DATA_FILE = "model.onnx_data";

    /**
     * 默认构造函数
     */
    public OnnxModelHolder() {
    }

    /**
     * 初始化方法，在 Bean 创建后自动调用。
     * 负责初始化 ONNX 运行时环境，根据配置加载外部或内置的模型，并创建执行会话。
     */
    @PostConstruct
    public void initialize() {
        try {
            log.info("正在初始化 ONNX Runtime 环境...");
            // 获取 ONNX 运行时环境
            this.env = OrtEnvironment.getEnvironment();
            
            String modelPathToUse;

            // 判断是否配置了外部模型路径
            if (externalModelPath != null && !externalModelPath.isBlank()) {
                log.info("使用外部 ONNX 模型：{}", externalModelPath);
                File modelFile = new File(externalModelPath);
                if (!modelFile.exists()) {
                    throw new IOException("External model file not found: " + externalModelPath);
                }
                modelPathToUse = modelFile.getAbsolutePath();
            } else {
                log.info("使用类路径下的内置 ONNX 模型");
                // 提取类路径下的内置模型文件到本次启动独立的临时目录，避免 Windows 文件映射锁导致覆盖失败
                Path tempDir = Files.createTempDirectory("novel-splitter-embedding-");
                this.extractedModelTempDir = tempDir;
                log.info("正在将内置 ONNX 资源提取到临时目录：{}", tempDir);
                
                File modelFile = extractResource(MODEL_RESOURCE_DIR + MODEL_FILE, tempDir.resolve(MODEL_FILE));
                extractResource(MODEL_RESOURCE_DIR + MODEL_DATA_FILE, tempDir.resolve(MODEL_DATA_FILE));
    
                if (modelFile == null) {
                    throw new IOException("Model file not found in resources: " + MODEL_RESOURCE_DIR + MODEL_FILE);
                }
                modelPathToUse = modelFile.getAbsolutePath();
            }

            log.info("正在从 {} 加载 ONNX 模型", modelPathToUse);
            
            // 配置 ONNX Session 的选项
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            if ("CUDAExecutionProvider".equalsIgnoreCase(provider)) {
                try {
                    // 尝试使用 CUDA 进行 GPU 加速（设备 ID 为 0）
                    options.addCUDA(0); // device id 0
                    log.info("ONNX 已配置使用 CUDAExecutionProvider");
                } catch (Exception e) {
                    // 如果 CUDA 提供者加载失败，则退回到使用 CPU
                    log.warn("添加 CUDAExecutionProvider 失败，已回退到 CPU。错误信息：{}", e.getMessage());
                }
            } else {
                log.info("ONNX 已配置使用默认的 CPUExecutionProvider");
            }
            
            // 创建模型执行会话
            this.session = env.createSession(modelPathToUse, options);
            
            log.info("ONNX 模型加载成功。输入：{}", session.getInputNames());
            
        } catch (Exception e) {
            log.error("ONNX 模型初始化失败", e);
            throw new RuntimeException("Critical: Failed to load embedding model", e);
        }
    }
    
    /**
     * 从类路径中提取指定的资源文件到目标路径。
     *
     * @param resourcePath 类路径中的资源文件路径
     * @param targetPath 提取后存储的目标文件路径
     * @return 提取后的目标文件对象，如果类路径中不存在该资源则返回 null
     * @throws IOException 文件读取或写入过程中的 IO 异常
     */
    private File extractResource(String resourcePath, Path targetPath) throws IOException {
        String safeResourcePath = Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        ClassPathResource resource = new ClassPathResource(safeResourcePath);
        if (!resource.exists()) {
             log.warn("未找到资源：{}", resourcePath);
             return null;
        }
        
        try (InputStream is = resource.getInputStream();
             FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
            // 将资源流拷贝到目标文件
            FileCopyUtils.copy(is, fos);
        }
        return targetPath.toFile();
    }
    
    /**
     * 在 Bean 销毁前调用的清理方法。
     * 负责关闭 ONNX 模型执行会话和运行时环境，释放底层内存资源。
     */
    @PreDestroy
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (OrtException e) {
            log.error("关闭 ONNX 资源时出错", e);
        } finally {
            cleanupTempDir();
        }
    }

    private void cleanupTempDir() {
        if (extractedModelTempDir == null) {
            return;
        }
        try (var paths = Files.walk(extractedModelTempDir)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("删除临时路径失败：{}", path, e);
                        }
                    });
            log.info("已清理提取的 ONNX 临时目录：{}", extractedModelTempDir);
        } catch (IOException e) {
            log.warn("清理提取的 ONNX 临时目录失败：{}", extractedModelTempDir, e);
        } finally {
            extractedModelTempDir = null;
        }
    }

    /**
     * 获取当前初始化的 ONNX 运行时环境。
     *
     * @return ONNX 运行时环境对象 {@link OrtEnvironment}
     */
    public OrtEnvironment getEnv() {
        return env;
    }

    /**
     * 获取当前加载的模型执行会话。
     *
     * @return ONNX 模型执行会话对象 {@link OrtSession}
     */
    public OrtSession getSession() {
        return session;
    }
}