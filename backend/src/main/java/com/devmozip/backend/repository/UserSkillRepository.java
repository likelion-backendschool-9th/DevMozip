package com.devmozip.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.UserSkill;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
}
