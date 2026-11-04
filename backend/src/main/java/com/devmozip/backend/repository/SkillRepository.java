package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.Skill;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    // 회원가입 시 화면에서 선택한 기술 스택 이름으로 조회
    List<Skill> findAllByNameIn(Collection<String> names);
}
