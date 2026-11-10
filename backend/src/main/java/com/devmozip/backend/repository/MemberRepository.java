package com.devmozip.backend.repository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 탈퇴와 신규 프로젝트 생성·지원 승인을 직렬화한다. 잠금 대기 후 DB의 현재 상태로 다시 검사한다.
    @Query(value = "SELECT * FROM users WHERE id=:id AND email IS NOT NULL AND status IN ('ACTIVE','WARNED') FOR UPDATE", nativeQuery = true)
    Optional<Member> findActiveForAccountUpdateById(@Param("id") Long id);
    Optional<Member> findByTagAndStatus(String tag, MemberStatus status);
    // 사용자 신고 대상은 계정 상태와 무관하게 찾는다
    Optional<Member> findByTag(String tag);
    // 1:1 방 생성과 사용자 신고처럼 DB 제약 없이 중복을 막는 작업을 회원 단위로 한 줄로 세운다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findForMessengerById(@Param("id") Long id);
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);
    boolean existsByTagIgnoreCaseAndIdNot(String tag, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.email = :email")
    Optional<Member> findForImageUpdateByEmail(@Param("email") String email);

    boolean existsByProfileImageOrBannerImage(String profileImage, String bannerImage);

    @Query("select (count(m) > 0) from Member m where m.status = :status and (m.profileImage = :url or m.bannerImage = :url)")
    boolean existsPublicImage(@Param("url") String url, @Param("status") MemberStatus status);

    // 로그인 시 이메일로 회원 조회
    Optional<Member> findByEmail(String email);

    // 카카오 로그인 시 카카오 회원번호로 회원 조회
    Optional<Member> findByKakaoId(String kakaoId);

    // 비밀번호 설정·카카오 연동 변경처럼 상태를 확인한 뒤 바꾸는 작업이 겹치지 않도록 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.email = :email")
    Optional<Member> findForAccountUpdateByEmail(@Param("email") String email);

    // 회원가입 시 이메일 중복 확인
    boolean existsByEmail(String email);

    // 회원가입 시 닉네임 중복 확인 (대소문자는 구분하지 않는다)
    boolean existsByNicknameIgnoreCase(String nickname);

    // 회원가입 시 이메일 앞부분을 태그로 쓸 수 있는지 확인 (프로필 수정처럼 대소문자는 구분하지 않는다)
    boolean existsByTagIgnoreCase(String tag);

    // 서버 시작 시 정지된 회원 목록 불러오기 (SuspendedMemberStore)
    List<Member> findAllByStatus(MemberStatus status);
}
