package com.devmozip.backend.dto.message;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 읽음 위치 변경 신호. 프로젝트 방은 읽은 사람을 memberTag로 알린다. 1:1 방에는 없다. */
public record MessageReadFrame(String type, String roomType, long roomId, long lastReadMessageId,
	@JsonInclude(JsonInclude.Include.NON_NULL) String memberTag) {
	public static MessageReadFrame direct(long roomId, long lastReadMessageId) {
		return new MessageReadFrame("message.read", "direct", roomId, lastReadMessageId, null);
	}

	/** roomId는 프로젝트 ID다. 태그가 없는 회원이면 memberTag를 넣지 않는다. */
	public static MessageReadFrame project(long projectId, long lastReadMessageId, String memberTag) {
		return new MessageReadFrame("message.read", "project", projectId, lastReadMessageId, memberTag);
	}
}
