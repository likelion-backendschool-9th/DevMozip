package com.devmozip.backend.dto.member;

import java.math.BigDecimal;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.CollaborationStatus;
import com.devmozip.backend.entity.Member.MemberRole;

import lombok.Builder;
import lombok.Data;

// 공개 프로필 정보
// GET /api/v1/users/{slug}
// 이메일·비밀번호 해시·카카오 ID·계정 상태·결제 수단은 공개 프로필에 내보내지 않는다.
@Data
@Builder
public class MemberResponse {
    private Long id;
    private Long jobCategoryId;
    private String nickname;
    private String tag;
    private MemberRole role;    // 프리미엄(PRO) 뱃지 표시용
    private String externalLinks;
    private BigDecimal trustScore;
    private CollaborationStatus collaborationStatus;
    private String profileImage;
    private String bannerImage;
    private String summary;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .jobCategoryId(member.getJobCategoryId())
                .nickname(member.getNickname())
                .tag(member.getTag())
                .role(member.getRole())
                .externalLinks(member.getExternalLinks())
                .trustScore(member.getTrustScore())
                .collaborationStatus(member.getCollaborationStatus())
                .profileImage(member.getProfileImage())
                .bannerImage(member.getBannerImage())
                .summary(member.getSummary())
                .build();
    }
}
