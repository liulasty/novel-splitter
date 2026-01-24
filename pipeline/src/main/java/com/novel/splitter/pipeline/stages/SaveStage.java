package com.novel.splitter.pipeline.stages;

import com.novel.splitter.pipeline.api.Stage;
import com.novel.splitter.pipeline.context.PipelineContext;
import com.novel.splitter.repository.api.SceneRepository;

/**
 * 持久化阶段
 * 将结果保存到文件
 */
public class SaveStage implements Stage {
    private final SceneRepository sceneRepository;

    public SaveStage(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    @Override
    public void process(PipelineContext context) {
        sceneRepository.saveScenes(context.getNovelName(), context.getVersion(), context.getScenes());
    }
}
