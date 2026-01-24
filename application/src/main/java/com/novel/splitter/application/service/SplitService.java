package com.novel.splitter.application.service;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.pipeline.context.PipelineContext;
import com.novel.splitter.pipeline.impl.SequentialPipeline;
import com.novel.splitter.pipeline.stages.LoadStage;
import com.novel.splitter.pipeline.stages.SaveStage;
import com.novel.splitter.pipeline.stages.SplitStage;
import com.novel.splitter.pipeline.stages.ValidationStage;
import com.novel.splitter.repository.api.NovelRepository;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.repository.impl.LocalFileNovelRepository;
import com.novel.splitter.repository.impl.LocalFileSceneRepository;
import com.novel.splitter.validation.impl.LengthValidator;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 切分服务
 * 负责构建和执行 Pipeline
 */
@Service
public class SplitService {

    private final AppConfig appConfig;
    private final NovelRepository novelRepository;
    private final SceneRepository sceneRepository;

    public SplitService(AppConfig appConfig) {
        this.appConfig = appConfig;
        
        // 初始化 Repository（这里简单起见直接实例化，也可以配置为 Bean）
        String storageRoot = appConfig.getStorage().getRootPath();
        this.novelRepository = new LocalFileNovelRepository();
        this.sceneRepository = new LocalFileSceneRepository(storageRoot);
    }

    /**
     * 执行切分任务
     *
     * @param filePath 小说文件路径
     * @param version  策略版本
     */
    public void executeSplit(String filePath, String version) {
        Path sourceFile = Paths.get(filePath);
        String novelName = getFileNameWithoutExtension(sourceFile.toFile());

        // 1. 构建 Pipeline
        SequentialPipeline pipeline = new SequentialPipeline()
                .addStage(new LoadStage(novelRepository))
                .addStage(new SplitStage()) // 这里可以传入配置的切分规则
                .addStage(new ValidationStage()
                        .addValidator(new LengthValidator(
                                appConfig.getRule().getMinLength(),
                                appConfig.getRule().getMaxLength()
                        )))
                .addStage(new SaveStage(sceneRepository));

        // 2. 构建 Context
        PipelineContext context = PipelineContext.builder()
                .novelName(novelName)
                .sourceFile(sourceFile)
                .version(version)
                .build();

        // 3. 执行
        pipeline.execute(context);
    }

    private String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return name;
        }
        return name.substring(0, lastIndexOf);
    }
}
