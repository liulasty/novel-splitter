package com.novel.splitter.application.mapper;

import com.novel.splitter.application.model.dto.AnswerDto;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.VectorRecordDto;
import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.model.dto.TaskProgressEventDto;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.TaskProgressEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

@Mapper
public interface DtoMapper {
    @Mapping(
            target = "paragraphCount",
            expression = "java(chapter.getEndParagraphIndex() >= chapter.getStartParagraphIndex() "
                    + "? chapter.getEndParagraphIndex() - chapter.getStartParagraphIndex() + 1 : 0)")
    ChapterDto toChapterDto(Chapter chapter);
    List<ChapterDto> toChapterDtos(List<Chapter> chapters);

    SceneDto toSceneDto(Scene scene);
    List<SceneDto> toSceneDtos(List<Scene> scenes);

    AnswerDto toAnswerDto(Answer answer);
    AnswerDto.CitationDto toCitationDto(Answer.Citation citation);

    VectorRecordDto toVectorRecordDto(VectorRecord vectorRecord);
    List<VectorRecordDto> toVectorRecordDtos(List<VectorRecord> vectorRecords);

    SplitTaskDto toSplitTaskDto(SplitTask task);
    List<SplitTaskDto> toSplitTaskDtos(List<SplitTask> tasks);

    TaskProgressEventDto toTaskProgressEventDto(TaskProgressEvent event);
    List<TaskProgressEventDto> toTaskProgressEventDtos(List<TaskProgressEvent> events);

    default int map(AtomicInteger value) {
        return value == null ? 0 : value.get();
    }
}