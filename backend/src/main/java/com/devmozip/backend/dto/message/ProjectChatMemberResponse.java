package com.devmozip.backend.dto.message;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import com.devmozip.backend.config.MessengerConfiguration;
import com.devmozip.backend.entity.ChatMember;
import com.devmozip.backend.entity.Member;

/**
 * 프로젝트 채팅의 참여자. 나간 회원도 포함한다. id는 이 방의 참여 행 ID이고 me는 요청한 회원인지다.
 * joinedAt과 leftAt은 승인·취소 시각이 아니라 채팅에 처음 반영된 시각이다.
 */
public record ProjectChatMemberResponse(
	long id,
	boolean me,
	String tag,
	String nickname,
	String profileImage,
	String role,
	Long lastReadMessageId,
	OffsetDateTime joinedAt,
	OffsetDateTime leftAt
) {
	public static ProjectChatMemberResponse from(ChatMember chatMember, boolean me, String role, Long lastReadMessageId) {
		Member member = chatMember.getMember();
		return new ProjectChatMemberResponse(
			chatMember.getId(),
			me,
			member.getTag(),
			member.getNickname(),
			member.getProfileImage(),
			role,
			lastReadMessageId,
			offset(chatMember.getJoinedAt()),
			offset(chatMember.getLeftAt()));
	}

	private static OffsetDateTime offset(LocalDateTime value) {
		return value == null ? null : value.atZone(MessengerConfiguration.ZONE).toOffsetDateTime();
	}
}
