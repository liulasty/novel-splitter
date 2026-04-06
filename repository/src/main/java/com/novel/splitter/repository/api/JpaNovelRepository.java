package com.novel.splitter.repository.api;

import com.novel.splitter.domain.entity.JpaNovelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaNovelRepository extends JpaRepository<JpaNovelEntity, String> {
}
