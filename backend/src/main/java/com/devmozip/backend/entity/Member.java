package com.devmozip.backend.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    // 권한 - 스프링 시큐리티의 hasRole("USER"), hasRole("ADMIN")과 이름을 맞춘다.
    public enum MemberRole {
        USER,
        PREMIUM,
        OWNER,
        ADMIN
    }

    // 계정 상태 - 관리자 화면의 경고/정지/해제 기능과 맞춘다.
    public enum MemberStatus {
        ACTIVE,     // 정상
        WARNED,     // 경고
        SUSPENDED   // 정지
    }

    // 협업 가능 상태
    public enum CollaborationStatus {
        AVAILABLE,      // 협업 가능
        RESTING,        // 쉬는 중
        BUSY,           // 현재 협업 어려움
        OPEN_TO_OFFERS, // 제안 검토 가능
        UNAVAILABLE     // 협업 불가
    }

    // 사용자 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 직군 ID (FK) - 직군 엔티티가 생기면 연관관계로 바꾼다.
    @Column(name = "job_category_id")
    private Long jobCategoryId;

    // 이메일
    @Column(name = "email", length = 255, unique = true)
    private String email;

    // 비밀번호 해시 (카카오 가입 회원은 비어 있을 수 있다)
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    // 카카오 계정 ID
    @Column(name = "kakao_id", length = 100)
    private String kakaoId;

    // 닉네임
    @Column(name = "nickname", length = 50, unique = true)
    private String nickname;

    // 사용자 태그
    @Column(name = "tag", length = 100, unique = true)
    private String tag;

    // 권한
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private MemberRole role;

    // 계정 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MemberStatus status;

    // 외부 링크 (JSON 문자열)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_links")
    private String externalLinks;

    // 신뢰 온도
    @Column(name = "trust_score", precision = 5, scale = 2)
    private BigDecimal trustScore;

    // 결제 수단 (JSON 문자열)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_methods")
    private String paymentMethods;

    // 협업 가능 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_status")
    private CollaborationStatus collaborationStatus;

    // 프로필 사진
    @Column(name = "profile_image", length = 255)
    private String profileImage;

    // 배너 사진
    @Column(name = "banner_image", length = 255)
    private String bannerImage;

    // 한 줄 소개
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    // 경력 (개월 수, 신입이거나 입력하지 않으면 비어 있을 수 있다)
    @Column(name = "career", nullable = true)
    private Integer career;

    @Column(name = "contact_hours", length = 100)
    private String contactHours;

    public void updateProfile(String nickname, String tag, Long jobCategoryId, String summary,
                              Integer career, CollaborationStatus collaborationStatus) {
        this.nickname = nickname;
        this.tag = tag;
        this.jobCategoryId = jobCategoryId;
        this.summary = summary;
        this.career = career;
        this.collaborationStatus = collaborationStatus;
    }

    public void updateContactHours(String contactHours) { this.contactHours = contactHours; }
    public void changeProfileImage(String url) { this.profileImage = url; }
    public void changeBannerImage(String url) { this.bannerImage = url; }

    @Builder
    public Member(Long jobCategoryId, String email, String passwordHash, String kakaoId,
                  String nickname, String tag, MemberRole role, MemberStatus status,
                  String externalLinks, BigDecimal trustScore, String paymentMethods,
                  CollaborationStatus collaborationStatus, String profileImage,
                  String bannerImage, String summary, Integer career) {
        this.jobCategoryId = jobCategoryId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.tag = tag;
        this.role = role;
        this.status = status;
        this.externalLinks = externalLinks;
        this.trustScore = trustScore;
        this.paymentMethods = paymentMethods;
        this.collaborationStatus = collaborationStatus;
        this.profileImage = profileImage;
        this.bannerImage = bannerImage;
        this.summary = summary;
        this.career = career;
    }
}
