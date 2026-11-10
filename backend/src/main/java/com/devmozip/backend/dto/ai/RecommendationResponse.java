package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.devmozip.backend.entity.AiRecommendation;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.AiRecommendationRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponse(long requestId, long conversationId, Long messageId,
	AiRecommendationRequest.Status status, @JsonRawValue String conditions,
	LocalDateTime createdAt, LocalDateTime completedAt, List<Item> developers, List<Item> projects) {
	public record Item(long id, Long recommendedUserId, Long recommendedProjectId, BigDecimal matchScore,
		String basicReason, int rank, LocalDateTime createdAt, PublicDeveloper developer) {
		public static Item from(AiRecommendation result) {
			return new Item(result.getId(), result.getUserId(), result.getProjectId(), result.getMatchScore(),
				result.getBasicReason(), result.getRank(), result.getCreatedAt(), PublicDeveloper.from(result.getUser()));
		}
	}
	public record PublicDeveloper(String tag, String nickname, String profileImage, List<String> jobCategories, List<String> skills, String career, String collaborationStatus, BigDecimal trustScore) {
		static PublicDeveloper from(Member member) {
			// 기존 공개 프로필의 ACTIVE·직군·tag 조건을 유지한다. 비공개 연락처·포트폴리오는 제공하지 않는다.
			if (member == null || member.getStatus() != Member.MemberStatus.ACTIVE
				|| member.getTag() == null || !member.getTag().matches("[A-Za-z0-9_-]{1,100}")
				|| member.getJobCategories() == null || member.getJobCategories().isEmpty()) return null;
			return new PublicDeveloper(member.getTag(), member.getNickname(), member.getProfileImage(),
				member.getJobCategories(), member.getSkills() == null ? List.of() : member.getSkills(), member.getCareer(),
				member.getCollaborationStatus() == null ? null : member.getCollaborationStatus().name(), member.getTrustScore());
		}
	}
}
