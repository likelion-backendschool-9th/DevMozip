package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 로그인 시 이메일로 회원 조회
    Optional<Member> findByEmail(String email);

    // 회원가입 시 이메일 중복 확인
    boolean existsByEmail(String email);

    // 공개 프로필 조회 시 사용자 태그(slug)로 회원 조회
    Optional<Member> findByTag(String tag);

    // 프로필 수정 시 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 프로필 수정 시 사용자 태그 중복 확인
    boolean existsByTag(String tag);

    // 서버 시작 시 정지된 회원 목록 불러오기 (SuspendedMemberStore)
    List<Member> findAllByStatus(MemberStatus status);
}
