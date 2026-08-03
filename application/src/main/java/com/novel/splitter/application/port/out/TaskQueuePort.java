package com.novel.splitter.application.port.out;

import com.novel.splitter.domain.task.CleanupTaskMessage;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import com.novel.splitter.domain.task.SplitTaskMessage;

import java.util.List;

public interface TaskQueuePort {
    void sendLoad(SplitTaskMessage message);
    void sendSplit(SplitTaskMessage message);
    void sendEmbed(EmbedTaskMessage message);

    /** Fan-out 细粒度向量化子任务（可批量调用以减少 publish 次数）。 */
    void sendEmbedScenes(List<EmbedSceneTaskMessage> messages);

    void sendEnrich(EnrichTaskMessage message);

    /** 投递异步清理任务（向量集合 + 文件）。 */
    void sendCleanup(CleanupTaskMessage message);
}

