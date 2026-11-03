package com.devmozip.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공개 프로필 조회에 필요한 컬럼만 매핑한다. 인증 담당과 공유할 사용자 Entity다.
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_category_id", nullable = false)
    private JobCategory jobCategory;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String tag;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "banner_image")
    private String bannerImage;

    @Column(name = "collaboration_status")
    private String collaborationStatus;

    @Column(name = "trust_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal trustScore;
}
