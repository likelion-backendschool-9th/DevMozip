package com.devmozip.backend.dto.message;

/** 새 메시지 신호. 본문은 넣지 않고 받는 쪽이 after로 조회한다. */
public record MessageCreatedFrame(String type, String roomType, long roomId, long messageId) {
	public static MessageCreatedFrame direct(long roomId, long messageId) {
		return new MessageCreatedFrame("message.created", "direct", roomId, messageId);
	}

	/** roomId는 프로젝트 ID다. 화면의 프로젝트 채팅 주소와 같은 값이다. */
	public static MessageCreatedFrame project(long projectId, long messageId) {
		return new MessageCreatedFrame("message.created", "project", projectId, messageId);
	}
}
