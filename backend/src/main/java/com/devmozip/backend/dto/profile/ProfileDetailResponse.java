package com.devmozip.backend.dto.profile;

import com.devmozip.backend.entity.Member;
import java.math.BigDecimal;
import java.util.List;

public record ProfileDetailResponse(
        Long id,
        String tag,
        String nickname,
        List<String> jobCategories,
        String summary,
        String career,
        String profileImage,
        String bannerImage,
        String collaborationStatus,
        BigDecimal trustScore,
        String contactHours
) {
    public static ProfileDetailResponse from(Member user) {
        return new ProfileDetailResponse(
                user.getId(), user.getTag(), user.getNickname(),
                user.getJobCategories() == null ? List.of() : user.getJobCategories(),
                user.getSummary(), user.getCareer(), user.getProfileImage(), user.getBannerImage(),
                user.getCollaborationStatus() == null ? null : user.getCollaborationStatus().name(), user.getTrustScore(), user.getContactHours());
    }
}
