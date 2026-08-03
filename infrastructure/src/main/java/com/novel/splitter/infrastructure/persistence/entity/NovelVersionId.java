package com.novel.splitter.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * {@code novel_version} 表可嵌入复合主键：(novel_id, version_tag)。
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NovelVersionId implements Serializable {

    @Column(name = "novel_id", nullable = false)
    private String novelId;

    @Column(name = "version_tag", nullable = false)
    private String versionTag;
}
