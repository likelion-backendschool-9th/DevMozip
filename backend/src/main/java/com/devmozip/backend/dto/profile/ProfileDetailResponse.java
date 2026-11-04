package com.devmozip.backend.dto.profile;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.JobCategory;
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
        BigDecimal trustScore,
        String contactHours
) {
    public static ProfileDetailResponse from(Member user, JobCategory job) {
        return new ProfileDetailResponse(
                user.getId(), user.getTag(), user.getNickname(),
                new JobCategoryResponse(job.getId(), job.getName()),
                user.getSummary(), user.getCareer(), user.getProfileImage(), user.getBannerImage(),
                user.getCollaborationStatus() == null ? null : user.getCollaborationStatus().name(), user.getTrustScore(), user.getContactHours());
    }

    public record JobCategoryResponse(Long id, String name) {}
}
