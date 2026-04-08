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
import java.nio.file.Paths;

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
            log.info("Initializing ONNX Runtime Environment...");
            // 获取 ONNX 运行时环境
            this.env = OrtEnvironment.getEnvironment();
            
            String modelPathToUse;

            // 判断是否配置了外部模型路径
            if (externalModelPath != null && !externalModelPath.isBlank()) {
                log.info("Using external ONNX model from: {}", externalModelPath);
                File modelFile = new File(externalModelPath);
                if (!modelFile.exists()) {
                    throw new IOException("External model file not found: " + externalModelPath);
                }
                modelPathToUse = modelFile.getAbsolutePath();
            } else {
                log.info("Using bundled ONNX model from classpath");
                // 提取类路径下的内置模型文件到系统的临时目录中，供 ONNX 引擎读取
                Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "novel-splitter-embedding");
                if (!Files.exists(tempDir)) {
                    Files.createDirectories(tempDir);
                }
                
                File modelFile = extractResource(MODEL_RESOURCE_DIR + MODEL_FILE, tempDir.resolve(MODEL_FILE));
                extractResource(MODEL_RESOURCE_DIR + MODEL_DATA_FILE, tempDir.resolve(MODEL_DATA_FILE));
    
                if (modelFile == null) {
                    throw new IOException("Model file not found in resources: " + MODEL_RESOURCE_DIR + MODEL_FILE);
                }
                modelPathToUse = modelFile.getAbsolutePath();
            }

            log.info("Loading ONNX Model from {}", modelPathToUse);
            
            // 配置 ONNX Session 的选项
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            if ("CUDAExecutionProvider".equalsIgnoreCase(provider)) {
                try {
                    // 尝试使用 CUDA 进行 GPU 加速（设备 ID 为 0）
                    options.addCUDA(0); // device id 0
                    log.info("ONNX configured with CUDAExecutionProvider");
                } catch (Exception e) {
                    // 如果 CUDA 提供者加载失败，则退回到使用 CPU
                    log.warn("Failed to add CUDAExecutionProvider, falling back to CPU. Error: {}", e.getMessage());
                }
            } else {
                log.info("ONNX configured with default CPUExecutionProvider");
            }
            
            // 创建模型执行会话
            this.session = env.createSession(modelPathToUse, options);
            
            log.info("ONNX Model loaded successfully. Inputs: {}", session.getInputNames());
            
        } catch (Exception e) {
            log.error("Failed to initialize ONNX model", e);
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
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
             log.warn("Resource not found: {}", resourcePath);
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
            log.error("Error closing ONNX resources", e);
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