package com.devmozip.backend.service.impl;

import com.devmozip.backend.service.AiConversationService;

import com.devmozip.backend.entity.AiConversation;
import com.devmozip.backend.entity.AiMessage;
import com.devmozip.backend.repository.AiConversationRepository;
import com.devmozip.backend.repository.AiMessageRepository;
import com.devmozip.backend.repository.AiConversationDeletionRepository;
import com.devmozip.backend.security.ai.CurrentUserProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.devmozip.backend.dto.ai.CreateConversationRequest;
import com.devmozip.backend.dto.ai.CreateMessageRequest;
import com.devmozip.backend.dto.ai.ConversationResponse;
import com.devmozip.backend.dto.ai.ConversationDetailResponse;
import com.devmozip.backend.dto.ai.MessageResponse;

@Service
@Transactional(readOnly = true)
public class AiConversationServiceImpl implements AiConversationService {
	private final CurrentUserProvider currentUser;
	private final AiConversationRepository conversations;
	private final AiMessageRepository messages;
	private final AiConversationDeletionRepository deletion;

	public AiConversationServiceImpl(CurrentUserProvider currentUser, AiConversationRepository conversations,
		AiMessageRepository messages, AiConversationDeletionRepository deletion) {
		this.currentUser = currentUser;
		this.conversations = conversations;
		this.messages = messages;
		this.deletion = deletion;
	}

	@Transactional
	@Override
	public ConversationResponse create(CreateConversationRequest request) {
		long userId = currentUser.getCurrentUserId();
		String title = request == null ? null : request.title();
		if (title != null && title.length() > 255) throw new IllegalArgumentException("대화 제목은 255자 이하여야 합니다.");
		return ConversationResponse.from(conversations.save(new AiConversation(userId, title, LocalDateTime.now())));
	}

	@Override
	public List<ConversationResponse> list() {
		return conversations.findByUserIdOrderByCreatedAtDescIdDesc(currentUser.getCurrentUserId()).stream()
			.map(ConversationResponse::from).toList();
	}

	@Override
	public ConversationDetailResponse detail(long id) {
		AiConversation conversation = owned(id, false);
		return new ConversationDetailResponse(conversation.getId(), conversation.getTitle(), conversation.getCurrentConditions(),
			conversation.getCreatedAt(), conversation.getUpdatedAt(), messages.findByConversationIdOrderByCreatedAtAscIdAsc(id)
				.stream().map(MessageResponse::from).toList());
	}

	@Transactional
	@Override
	public MessageResponse addMessage(long id, CreateMessageRequest request) {
		AiConversation conversation = owned(id, true);
		if (request == null || request.content() == null || request.content().isBlank()) {
			throw new IllegalArgumentException("메시지 내용을 입력해 주세요.");
		}
		LocalDateTime now = LocalDateTime.now();
		AiMessage message = messages.save(new AiMessage(conversation, request.content(), now));
		conversation.touch(now);
		return MessageResponse.from(message);
	}

	@Transactional
	@Override
	public void delete(long id) {
		AiConversation conversation = owned(id, true);
		deletion.deleteChildren(id);
		conversations.delete(conversation);
		conversations.flush();
	}

	private AiConversation owned(long id, boolean lock) {
		long userId = currentUser.getCurrentUserId();
		if (id <= 0) throw new IllegalArgumentException("대화 ID를 확인해 주세요.");
		return (lock ? conversations.findOwnedForUpdate(id, userId) : conversations.findByIdAndUserId(id, userId))
			.orElseThrow(() -> new NoSuchElementException("대화를 찾을 수 없습니다."));
	}
}
