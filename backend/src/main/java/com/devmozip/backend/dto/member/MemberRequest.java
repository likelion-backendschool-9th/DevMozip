package com.devmozip.backend.dto.member;

import com.devmozip.backend.entity.Member.CollaborationStatus;

import lombok.Data;
import lombok.NoArgsConstructor;

// 회원이 직접 수정하는 프로필 정보
// PUT /api/v1/users/me/profile       (초기 개발자 프로필 설정, 프로필 수정)
// PUT /api/v1/users/me/links         (외부 링크 관리)
// PATCH /api/v1/users/me/availability (협업 가능 상태 관리)
@Data
@NoArgsConstructor
public class MemberRequest {
    private Long jobCategoryId;
    private String nickname;
    private String tag;
    private String externalLinks;
    private CollaborationStatus collaborationStatus;
    private String profileImage;
    private String bannerImage;
    private String summary;
    private Integer career;     // 경력 (개월 수)
}
