package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByTagAndStatus(String tag, MemberStatus status);
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);
    boolean existsByTagIgnoreCaseAndIdNot(String tag, Long id);

    // 로그인 시 이메일로 회원 조회
    Optional<Member> findByEmail(String email);

    // 회원가입 시 이메일 중복 확인
    boolean existsByEmail(String email);

    // 서버 시작 시 정지된 회원 목록 불러오기 (SuspendedMemberStore)
    List<Member> findAllByStatus(MemberStatus status);
}
