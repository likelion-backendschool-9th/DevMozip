package com.devmozip.backend.service.impl;

import com.devmozip.backend.dto.ai.RecommendationResponse;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import com.devmozip.backend.service.AiRecommendationExecutionService;
import com.devmozip.backend.service.AiRecommendationService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiRecommendationExecutionServiceImpl implements AiRecommendationExecutionService {
	private static final Logger log = LoggerFactory.getLogger(AiRecommendationExecutionServiceImpl.class);
	private final CurrentUserProvider currentUser;
	private final AiConversationRepository conversations;
	private final AiMessageRepository messages;
	private final AiRecommendationRequestRepository requests;
	private final AiRecommendationRepository results;
	private final AiRecommendationService engine;
	private final ObjectMapper mapper;
	private final EntityManager entityManager;
	private final TransactionTemplate transaction;
	private final TransactionTemplate read;

	public AiRecommendationExecutionServiceImpl(CurrentUserProvider currentUser, AiConversationRepository conversations,
		AiMessageRepository messages, AiRecommendationRequestRepository requests, AiRecommendationRepository results,
		AiRecommendationService engine, ObjectMapper mapper, EntityManager entityManager, PlatformTransactionManager manager) {
		this.currentUser = currentUser;
		this.conversations = conversations;
		this.messages = messages;
		this.requests = requests;
		this.results = results;
		this.engine = engine;
		this.mapper = mapper;
		this.entityManager = entityManager;
		transaction = new TransactionTemplate(manager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		read = new TransactionTemplate(manager);
		read.setReadOnly(true);
		read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}

	@Override
	public Execution execute(long conversationId, Long messageId, String key) {
		long userId = currentUser.getCurrentUserId();
		if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw bad("Idempotency-Key는 영문·숫자·._:-로 1~128자여야 합니다.");
		var started = transaction.execute(tx -> {
			var conversation = owned(conversationId, userId, true);
			UpdateConditionsRequest conditions = conditions(conversation.getCurrentConditions());
			AiMessage message = messageId == null ? null : messages.findByIdAndConversationId(messageId, conversationId)
				.orElseThrow(() -> bad("동일 대화의 USER 메시지를 지정해 주세요."));
			if (message != null && (message.getRole() != AiMessage.Role.USER || message.getContent().length() > 10000)) {
				throw bad("동일 대화의 10000자 이하 USER 메시지를 지정해 주세요.");
			}
			var previous = requests.findByConversationIdAndIdempotencyKey(conversationId, key);
			if (previous.isPresent()) {
				var item = previous.get();
				if (!Objects.equals(messageId, item.getMessageId()) || !conditions.equals(conditions(item.getConditions()))) {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 키에 다른 추천 입력을 사용할 수 없습니다.");
				}
				return new Execution(response(conversationId, item), false);
			}
			var request = requests.saveAndFlush(new AiRecommendationRequest(conversation, message, mapper.writeValueAsString(conditions), key));
			return new Execution(response(conversationId, request), true);
		});
		if (!started.created()) return started;
		long requestId = started.response().requestId();
		try {
			return transaction.execute(tx -> {
				owned(conversationId, userId, true);
				var request = request(conversationId, requestId);
				var calculated = engine.recommend(conditions(request.getConditions()), request.getMessageContent());
				int rank = 1;
				for (var developer : calculated.developers()) results.save(new AiRecommendation(request,
					entityManager.getReference(Member.class, developer.id()), null, developer.score().total(),
					String.join("\n", developer.score().basicReasons()), rank++));
				rank = 1;
				for (var project : calculated.projects()) results.save(new AiRecommendation(request, null,
					entityManager.getReference(Project.class, project.id()), project.score().total(),
					String.join("\n", project.score().basicReasons()), rank++));
				results.flush();
				request.complete(AiRecommendationRequest.Status.SUCCESS);
				requests.flush();
				return new Execution(response(conversationId, request), true);
			});
		} catch (RuntimeException failure) {
			log.error("추천 요청 {} 실행 실패", requestId, failure);
			return transaction.execute(tx -> {
				owned(conversationId, userId, true);
				var request = request(conversationId, requestId);
				request.complete(AiRecommendationRequest.Status.FAILED);
				requests.flush();
				return new Execution(response(conversationId, request), true);
			});
		}
	}

	@Override
	public RecommendationResponse find(long conversationId, Long requestId) {
		long userId = currentUser.getCurrentUserId();
		return read.execute(tx -> {
			owned(conversationId, userId, false);
			if (requestId != null && requestId <= 0) throw bad("추천 요청 ID를 확인해 주세요.");
			var request = requestId == null ? requests.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId)
				.orElseThrow(AiRecommendationExecutionServiceImpl::notFound) : request(conversationId, requestId);
			return response(conversationId, request);
		});
	}

	private AiConversation owned(long id, long userId, boolean lock) {
		if (id <= 0) throw bad("대화 ID를 확인해 주세요.");
		return (lock ? conversations.findOwnedForUpdate(id, userId) : conversations.findByIdAndUserId(id, userId))
			.orElseThrow(AiRecommendationExecutionServiceImpl::notFound);
	}
	private AiRecommendationRequest request(long conversationId, long requestId) {
		return requests.findByIdAndConversationId(requestId, conversationId).orElseThrow(AiRecommendationExecutionServiceImpl::notFound);
	}
	private UpdateConditionsRequest conditions(String json) {
		try { return Objects.requireNonNull(mapper.readValue(json, UpdateConditionsRequest.class)); }
		catch (RuntimeException invalid) { throw bad("저장된 추천 조건을 확인해 주세요."); }
	}
	private RecommendationResponse response(long conversationId, AiRecommendationRequest request) {
		List<RecommendationResponse.Item> items = results.findByRequestIdOrderByRankAscIdAsc(request.getId())
			.stream().map(RecommendationResponse.Item::from).toList();
		return new RecommendationResponse(request.getId(), conversationId, request.getMessageId(), request.getStatus(),
			request.getConditions(), request.getCreatedAt(), request.getCompletedAt(),
			items.stream().filter(item -> item.recommendedUserId() != null).toList(),
			items.stream().filter(item -> item.recommendedProjectId() != null).toList());
	}
	private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
	private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "대화 또는 추천 요청을 찾을 수 없습니다."); }
}
