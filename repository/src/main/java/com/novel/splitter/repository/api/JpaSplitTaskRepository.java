package com.novel.splitter.repository.api;

import com.novel.splitter.domain.entity.JpaSplitTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaSplitTaskRepository extends JpaRepository<JpaSplitTaskEntity, String> {
}
