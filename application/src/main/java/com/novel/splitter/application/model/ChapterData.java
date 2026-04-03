package com.novel.splitter.application.model;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterData {
    private Chapter chapter;
    private List<RawParagraph> paragraphs;
}
