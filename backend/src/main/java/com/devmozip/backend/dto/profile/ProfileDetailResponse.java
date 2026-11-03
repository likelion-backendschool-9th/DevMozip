package com.devmozip.backend.dto.profile;

import com.devmozip.backend.entity.User;
import java.math.BigDecimal;

public record ProfileDetailResponse(
        Long id,
        String tag,
        String nickname,
        JobCategoryResponse jobCategory,
        String summary,
        Integer career,
        String profileImage,
        String bannerImage,
        String collaborationStatus,
        BigDecimal trustScore
) {
    public static ProfileDetailResponse from(User user) {
        return new ProfileDetailResponse(
                user.getId(), user.getTag(), user.getNickname(),
                new JobCategoryResponse(user.getJobCategory().getId(), user.getJobCategory().getName()),
                user.getSummary(), user.getCareer(), user.getProfileImage(), user.getBannerImage(),
                user.getCollaborationStatus(), user.getTrustScore());
    }

    public record JobCategoryResponse(Long id, String name) {}
}
