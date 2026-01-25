package com.novel.splitter.embedding;

import ai.onnxruntime.OrtEnvironment;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OnnxRuntimeTest {

    @Test
    public void testOnnxRuntimeLoad() {
        // 验证 ONNX Runtime 能够被 JVM 加载
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        assertNotNull(env, "OrtEnvironment should not be null");
        env.close();
    }

    @Test
    public void testModelResourceExists() {
        // 验证 model.onnx 能够通过 ClassLoader 读取
        try (InputStream is = getClass().getResourceAsStream("/embedding/model.onnx")) {
            assertNotNull(is, "model.onnx should be found in resources");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    public void testVocabResourceExists() {
        // 验证 vocab.txt 能够通过 ClassLoader 读取
        try (InputStream is = getClass().getResourceAsStream("/embedding/vocab.txt")) {
            assertNotNull(is, "vocab.txt should be found in resources");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
