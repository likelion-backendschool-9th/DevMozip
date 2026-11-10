package com.devmozip.backend.dto.message;

import java.util.List;
import com.devmozip.backend.entity.Member;

public record ChatPeerResponse(
	String tag,
	String nickname,
	String profileImage,
	List<String> jobCategories,
	String collaborationStatus
) {
	public static ChatPeerResponse from(Member member) {
		return new ChatPeerResponse(
			member.getTag(),
			member.getNickname(),
			member.getProfileImage(),
			member.getJobCategories() == null ? List.of() : member.getJobCategories(),
			member.getCollaborationStatus() == null ? null : member.getCollaborationStatus().name());
	}
}
