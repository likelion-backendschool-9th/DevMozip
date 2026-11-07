package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiDraftService {
	private final CurrentUserProvider currentUser;
	private final AiConversationRepository conversations;
	private final AiMessageRepository messages;
	private final AiRecommendationRequestRepository requests;
	private final ObjectMapper mapper;
	private final TransactionTemplate transaction;

	public AiDraftService(CurrentUserProvider currentUser, AiConversationRepository conversations,
		AiMessageRepository messages, AiRecommendationRequestRepository requests, ObjectMapper mapper,
		PlatformTransactionManager manager) {
		this.currentUser = currentUser;
		this.conversations = conversations;
		this.messages = messages;
		this.requests = requests;
		this.mapper = mapper;
		transaction = new TransactionTemplate(manager);
	}

	public ConversationDetailResponse sync(SyncDraftRequest request) {
		long userId = currentUser.getCurrentUserId();
		try { return transaction.execute(tx -> save(userId, request)); }
		catch (DataIntegrityViolationException collision) {
			// Concurrent first uploads can collide on the unique key. Retry after the first transaction commits.
			return transaction.execute(tx -> save(userId, request));
		}
	}

	private ConversationDetailResponse save(long userId, SyncDraftRequest request) {
		AiConversation conversation;
		if (request.conversationId() != null) {
			conversation = owned(request.conversationId(), userId);
		} else {
			var previous = conversations.findByUserIdAndClientKey(userId, request.clientKey());
			if (previous.isPresent()) conversation = owned(previous.get().getId(), userId);
			else {
				conversation = new AiConversation(userId, request.title(), LocalDateTime.now());
				conversation.setClientKey(request.clientKey());
				conversation = conversations.saveAndFlush(conversation);
			}
		}
		for (var input : request.messages()) {
			var previous = messages.findByConversationIdAndClientKey(conversation.getId(), input.clientKey());
			if (previous.isPresent()) {
				if (!previous.get().getContent().equals(input.content())) {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 메시지 키의 내용을 변경할 수 없습니다.");
				}
			} else {
				var message = new AiMessage(conversation, input.content(), LocalDateTime.now());
				message.setClientKey(input.clientKey());
				messages.save(message);
			}
		}
		conversation.replaceConditions(mapper.writeValueAsString(request.conditions()), LocalDateTime.now());
		messages.flush();
		return new ConversationDetailResponse(conversation.getId(), conversation.getTitle(), conversation.getCurrentConditions(),
			conversation.getCreatedAt(), conversation.getUpdatedAt(), messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId())
				.stream().map(MessageResponse::from).toList(), conversation.getClientKey());
	}

	public List<RecommendationRequestSummary> history(long conversationId) {
		long userId = currentUser.getCurrentUserId();
		return transaction.execute(tx -> {
			owned(conversationId, userId);
			return requests.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId).stream()
				.map(r -> new RecommendationRequestSummary(r.getId(), r.getStatus(), r.getCreatedAt())).toList();
		});
	}
	private AiConversation owned(long id, long userId) {
		if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대화 ID를 확인해 주세요.");
		return conversations.findOwnedForUpdate(id, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."));
	}
}
