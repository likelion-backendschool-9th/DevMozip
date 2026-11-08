package com.devmozip.backend.utils;

import com.devmozip.backend.entity.AiMessage;
import java.util.List;

/** UTF-16 문자 수 기준. 기존 초과 기록은 읽을 수 있지만 추가 전송·추천은 제한한다. */
public final class AiMessageLimits {
	public static final int MESSAGE = 1500;
	public static final int CONVERSATION = 10000;
	private AiMessageLimits() {}

	public static void validateMessage(String content) {
		if (content == null || content.isBlank() || content.length() > MESSAGE) {
			throw new IllegalArgumentException("메시지는 1~1500자로 입력해 주세요.");
		}
	}

	public static long length(List<AiMessage> messages) {
		var users = messages.stream().filter(m -> m.getRole() == AiMessage.Role.USER).toList();
		return users.stream().mapToLong(m -> m.getContent().length()).sum() + Math.max(0, users.size() - 1) * 2L;
	}

	public static void validateTotal(long length) {
		if (length > CONVERSATION) throw new IllegalArgumentException("대화의 사용자 메시지는 합계 10000자까지 가능합니다. 새 대화를 시작해 주세요.");
	}
}
