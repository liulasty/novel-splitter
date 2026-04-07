package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaNovelRepository extends JpaRepository<JpaNovelEntity, String> {
    Optional<JpaNovelEntity> findByFileMd5(String fileMd5);
}
