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

@Slf4j
@Component
public class OnnxModelHolder {

    private OrtEnvironment env;
    private OrtSession session;
    
    @org.springframework.beans.factory.annotation.Value("${embedding.onnx.model-path:}")
    private String externalModelPath;

    private static final String MODEL_RESOURCE_DIR = "embedding/";
    private static final String MODEL_FILE = "model.onnx";
    private static final String MODEL_DATA_FILE = "model.onnx_data";

    public OnnxModelHolder() {
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing ONNX Runtime Environment...");
            this.env = OrtEnvironment.getEnvironment();
            
            String modelPathToUse;

            if (externalModelPath != null && !externalModelPath.isBlank()) {
                log.info("Using external ONNX model from: {}", externalModelPath);
                File modelFile = new File(externalModelPath);
                if (!modelFile.exists()) {
                    throw new IOException("External model file not found: " + externalModelPath);
                }
                modelPathToUse = modelFile.getAbsolutePath();
            } else {
                log.info("Using bundled ONNX model from classpath");
                // Extract model files to temp directory
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
            
            this.session = env.createSession(modelPathToUse, new OrtSession.SessionOptions());
            
            log.info("ONNX Model loaded successfully. Inputs: {}", session.getInputNames());
            
        } catch (Exception e) {
            log.error("Failed to initialize ONNX model", e);
            throw new RuntimeException("Critical: Failed to load embedding model", e);
        }
    }
    
    private File extractResource(String resourcePath, Path targetPath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
             log.warn("Resource not found: {}", resourcePath);
             return null;
        }
        
        try (InputStream is = resource.getInputStream();
             FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
            FileCopyUtils.copy(is, fos);
        }
        return targetPath.toFile();
    }
    
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

    public OrtEnvironment getEnv() {
        return env;
    }

    public OrtSession getSession() {
        return session;
    }
}
