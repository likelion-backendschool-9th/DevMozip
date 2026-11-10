package com.devmozip.backend.dto.message;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 입력 중 신호. 저장하지 않는다. 프로젝트 방은 누가 입력 중인지 표시하도록 닉네임을 보낸다. */
public record TypingFrame(String type, String roomType, long roomId, @JsonInclude(JsonInclude.Include.NON_NULL) String nickname) {
	public static TypingFrame direct(long roomId) {
		return new TypingFrame("typing", "direct", roomId, null);
	}

	/** roomId는 프로젝트 ID다. 닉네임이 없는 회원이면 nickname을 넣지 않는다. */
	public static TypingFrame project(long projectId, String nickname) {
		return new TypingFrame("typing", "project", projectId, nickname);
	}
}
