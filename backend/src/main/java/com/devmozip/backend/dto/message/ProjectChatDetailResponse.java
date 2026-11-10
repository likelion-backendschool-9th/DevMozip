package com.devmozip.backend.dto.message;

import java.util.List;

public record ProjectChatDetailResponse(
	long projectId,
	String title,
	String status,
	boolean canSend,
	boolean isHost,
	List<ProjectChatMemberResponse> members,
	List<ProjectChatMessageResponse> messages,
	Long nextBefore
) {
}
