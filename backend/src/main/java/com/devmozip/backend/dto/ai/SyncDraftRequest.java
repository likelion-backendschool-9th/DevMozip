package com.devmozip.backend.dto.ai;

import com.devmozip.backend.utils.StrictLongDeserializer;
import java.util.HashSet;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SyncDraftRequest(@JsonDeserialize(using = StrictLongDeserializer.class) Long conversationId,
	String clientKey, String title, List<Message> messages, UpdateConditionsRequest conditions) {
	public SyncDraftRequest {
		if (conversationId != null && conversationId <= 0) throw new IllegalArgumentException("대화 ID를 확인해 주세요.");
		if (clientKey == null || !clientKey.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("초안 키를 확인해 주세요.");
		if (title != null && title.length() > 255) throw new IllegalArgumentException("제목은 255자 이하여야 합니다.");
		if (conditions == null || messages == null) throw new IllegalArgumentException("메시지와 조건이 필요합니다.");
		var keys = new HashSet<String>();
		for (var message : messages) {
			if (message == null || !keys.add(message.clientKey())) throw new IllegalArgumentException("중복 메시지 키입니다.");
		}
		messages = List.copyOf(messages);
	}
	public record Message(String clientKey, String content) {
		public Message {
			if (clientKey == null || !clientKey.matches("[A-Za-z0-9._:-]{1,128}")
				|| content == null || content.isBlank() || content.length() > 1500) {
				throw new IllegalArgumentException("메시지 키와 1~1500자 내용을 확인해 주세요.");
			}
		}
	}
}
