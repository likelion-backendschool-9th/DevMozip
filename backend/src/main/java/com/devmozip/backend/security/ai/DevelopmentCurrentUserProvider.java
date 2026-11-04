package com.devmozip.backend.security.ai;

import com.devmozip.backend.repository.AiConversationRepository;

import org.springframework.security.authentication.InsufficientAuthenticationException;

// Not a component: only CurrentUserConfiguration may enable this development adapter.
final class DevelopmentCurrentUserProvider implements CurrentUserProvider {
	private final AiConversationRepository conversations;
	private final Long userId;

	DevelopmentCurrentUserProvider(AiConversationRepository conversations, Long userId) {
		this.conversations = conversations;
		this.userId = userId;
	}

	@Override
	public long getCurrentUserId() {
		if (userId == null || userId <= 0 || !conversations.existsUserById(userId)) {
			throw new InsufficientAuthenticationException("AI 개발용 사용자 설정을 확인해 주세요.");
		}
		return userId;
	}
}
