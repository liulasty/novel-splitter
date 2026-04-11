package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaNovelRepository extends JpaRepository<JpaNovelEntity, String> {
    Optional<JpaNovelEntity> findFirstByTitle(String title);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM JpaNovelEntity n WHERE n.id = :id")
    Optional<JpaNovelEntity> findByIdForUpdate(@Param("id") String id);
}
