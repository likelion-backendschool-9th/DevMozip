package com.devmozip.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.JobCategory;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Long> {
    // 회원가입 시 화면에서 선택한 직군 이름으로 조회
    Optional<JobCategory> findByName(String name);
}
