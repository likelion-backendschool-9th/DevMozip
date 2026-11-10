package com.devmozip.backend.entity;

import java.math.BigDecimal;
import java.util.List;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@org.hibernate.annotations.DynamicUpdate
// 이미 있는 users 테이블에도 제약이 추가되도록 @Column(unique)가 아니라 여기에 둔다.
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "kakao_id")
})
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

    // 이메일 (카카오 가입 회원은 비어 있을 수 있어 not null은 걸지 않는다)
    @Column(name = "email", length = 255)
    private String email;

    // 비밀번호 해시 (카카오 가입 회원은 비어 있을 수 있다)
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    // 카카오 회원번호 (카카오 API의 id. 토큰은 저장하지 않는다)
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

    // 경력 (가입 화면에서 선택한 문구를 그대로 저장한다. 예: 3년 차)
    @Column(name = "career", length = 30)
    private String career;

    // 연락 가능 시간
    @Column(name = "contact_hours", length = 100)
    private String contactHours;

    // 개발 직군 (허용 값은 entity.enums에서 관리하고 고른 문구를 저장한다)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_categories")
    private List<String> jobCategories;

    // 기술 스택
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    private List<String> skills;

    // 관심 분야
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interests")
    private List<String> interests;

    // 상세 자기소개는 상단 한 줄 소개(summary)와 별도로 관리한다.
    @Column(name = "introduction", columnDefinition = "text")
    private String introduction;

    // 상세 자기소개 작성 형식 (text 또는 html)
    @Column(name = "introduction_format", length = 10)
    private String introductionFormat;

    public void updateExternalLinks(String externalLinks) { this.externalLinks = externalLinks; }

    public void updateProfileOptions(List<String> skills, List<String> interests) {
        this.skills = List.copyOf(skills);
        this.interests = List.copyOf(interests);
    }

    public void updateIntroduction(String introduction, String introductionFormat) {
        this.introduction = introduction;
        this.introductionFormat = introductionFormat;
    }

    public void updateProfile(String nickname, String tag, List<String> jobCategories, String summary,
                              String career, CollaborationStatus collaborationStatus) {
        this.nickname = nickname;
        this.tag = tag;
        this.jobCategories = jobCategories;
        this.summary = summary;
        this.career = career;
        this.collaborationStatus = collaborationStatus;
    }

    public void updateContactHours(String contactHours) { this.contactHours = contactHours; }
    public void changeProfileImage(String url) { this.profileImage = url; }
    public void changeBannerImage(String url) { this.bannerImage = url; }
    public void linkKakao(String kakaoId) { this.kakaoId = kakaoId; }
    public void unlinkKakao() { this.kakaoId = null; }
    public void changePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    // 결제 수단 (docs/premium-payment-plan.md 4절). 빌링키는 암호화된 값으로만 들어온다. null이면 등록된 결제 수단이 없다.
    public void changePaymentMethods(String paymentMethods) { this.paymentMethods = paymentMethods; }

    // 구독 결제는 USER ↔ PREMIUM만 바꾼다. 다른 역할 관리는 관리자 범위다.
    public void upgradeToPremium() {
        if (role != null && role != MemberRole.USER) {
            throw new IllegalStateException("USER만 프리미엄으로 바꿀 수 있습니다.");
        }
        this.role = MemberRole.PREMIUM;
    }

    public void downgradeFromPremium() {
        if (role == MemberRole.PREMIUM) {
            this.role = MemberRole.USER;
        }
    }

    @Builder
    public Member(String email, String passwordHash, String kakaoId,
                  String nickname, String tag, MemberRole role, MemberStatus status,
                  String externalLinks, BigDecimal trustScore, String paymentMethods,
                  CollaborationStatus collaborationStatus, String profileImage,
                  String bannerImage, String summary, String career, String contactHours,
                  List<String> jobCategories, List<String> skills, List<String> interests,
                  String introduction, String introductionFormat) {
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
        this.contactHours = contactHours;
        this.jobCategories = jobCategories;
        this.skills = skills;
        this.interests = interests;
        this.introduction = introduction;
        this.introductionFormat = introductionFormat;
    }

    // 협업 가능 상태와 연락 가능 시간을 바꾼다. null로 받은 값은 그대로 둔다.
    public void updateAvailability(CollaborationStatus collaborationStatus, String contactHours) {
        if (collaborationStatus != null) {
            this.collaborationStatus = collaborationStatus;
        }
        if (contactHours != null) {
            this.contactHours = contactHours;
        }
    }
}
