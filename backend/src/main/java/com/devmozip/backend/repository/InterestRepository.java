package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.Interest;

public interface InterestRepository extends JpaRepository<Interest, Long> {
    // 회원가입 시 화면에서 선택한 관심 분야 이름으로 조회
    List<Interest> findAllByNameIn(Collection<String> names);
}
