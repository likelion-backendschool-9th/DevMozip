package com.devmozip.backend.service;

import com.devmozip.backend.client.GeminiAnalysisClient;
import com.devmozip.backend.dto.ai.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import com.devmozip.backend.utils.AiInputPrivacy;
import com.devmozip.backend.utils.AiMessageLimits;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** USER 저장 → 외부 분석(트랜잭션 밖) → 조건/ASSISTANT 원자 저장. */
@Service
public class AiMessageAnalysisService {
	private final CurrentUserProvider currentUser;
	private final AiConversationRepository conversations;
	private final AiMessageRepository messages;
	private final GeminiAnalysisClient gemini;
	private final AiAnalysisMergePolicy merge;
	private final AiConditionsDocument documents;
	private final ObjectMapper mapper;
	private final TransactionTemplate transaction;
	public AiMessageAnalysisService(CurrentUserProvider currentUser, AiConversationRepository conversations,
		AiMessageRepository messages, GeminiAnalysisClient gemini, AiAnalysisMergePolicy merge, AiConditionsDocument documents,
		ObjectMapper mapper, PlatformTransactionManager manager) {
		this.currentUser = currentUser; this.conversations = conversations; this.messages = messages;
		this.gemini = gemini; this.merge = merge; this.documents = documents; this.mapper = mapper;
		transaction = new TransactionTemplate(manager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}
	private record Snapshot(String conditions, long userMessageId, long lastMessageId, String latest, String context,
		MessageAnalysisResponse replay) {}
	public MessageAnalysisResponse analyze(long conversationId, CreateMessageRequest input) {
		long userId = currentUser.getCurrentUserId();
		AiMessageLimits.validateMessage(input.content());
		if (input.clientKey() == null || !input.clientKey().matches("[A-Za-z0-9._:-]{1,128}")) {
			throw new IllegalArgumentException("분석 메시지의 clientKey가 필요합니다.");
		}
		String assistantKey = assistantKey(input.clientKey());
		Snapshot snapshot = transaction.execute(tx -> {
			var conversation = owned(conversationId, userId);
			var user = messages.findByConversationIdAndClientKey(conversationId, input.clientKey()).orElse(null);
			if (user != null && (user.getRole() != AiMessage.Role.USER || !user.getContent().equals(input.content()))) throw conflict();
			var prior = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
			if (user == null) {
				AiMessageLimits.validateTotal(AiMessageLimits.length(prior) + input.content().length()
					+ (prior.stream().anyMatch(m -> m.getRole() == AiMessage.Role.USER) ? 2 : 0));
				user = new AiMessage(conversation, input.content(), LocalDateTime.now());
				user.setClientKey(input.clientKey());
				messages.saveAndFlush(user);
				conversation.touch(LocalDateTime.now());
			}
			var assistant = messages.findByConversationIdAndClientKey(conversationId, assistantKey).orElse(null);
			if (assistant != null) {
				if (assistant.getRole() != AiMessage.Role.ASSISTANT) throw conflict();
				return new Snapshot(null, user.getId(), 0, null, null, response(conversation, user, assistant, true));
			}
			var history = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
			AiMessageLimits.validateTotal(AiMessageLimits.length(history));
			var latestUser = history.stream().filter(m -> m.getRole() == AiMessage.Role.USER).reduce((a, b) -> b).orElseThrow();
			if (!latestUser.getId().equals(user.getId())) throw conflict();
			String latest = AiInputPrivacy.redact(input.content());
			return new Snapshot(conversation.getCurrentConditions(), user.getId(), history.getLast().getId(), latest,
				context(conversation, history, latest), null);
		});
		if (snapshot.replay() != null) return snapshot.replay();
		var analysis = gemini.analyze(snapshot.context());
		AiAnalysisMergePolicy.Merged merged;
		try { merged = merge.merge(snapshot.conditions(), analysis, snapshot.latest(), snapshot.userMessageId()); }
		catch (RuntimeException invalid) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "분석된 조건이나 근거를 검증하지 못했습니다. 기존 조건은 유지됩니다.");
		}
		return transaction.execute(tx -> {
			var conversation = owned(conversationId, userId);
			var user = messages.findByIdAndConversationId(snapshot.userMessageId(), conversationId).orElseThrow(AiMessageAnalysisService::missing);
			var previous = messages.findByConversationIdAndClientKey(conversationId, assistantKey);
			if (previous.isPresent()) {
				if (previous.get().getRole() != AiMessage.Role.ASSISTANT) throw conflict();
				return response(conversation, user, previous.get(), true);
			}
			var history = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
			if (!conversation.getCurrentConditions().equals(snapshot.conditions())
				|| history.getLast().getId() != snapshot.lastMessageId()) throw conflict();
			conversation.replaceConditions(merged.document(), LocalDateTime.now());
			var assistant = messages.saveAndFlush(AiMessage.assistant(conversation, merged.reply(), assistantKey, LocalDateTime.now()));
			return response(conversation, user, assistant, false);
		});
	}
	private String context(AiConversation conversation, List<AiMessage> history, String latest) {
		var document = documents.read(conversation.getCurrentConditions());
		var userHistory = history.stream().filter(m -> m.getRole() == AiMessage.Role.USER)
			.map(m -> Map.of("role", "USER", "text", AiInputPrivacy.redact(m.getContent()))).toList();
		var assistantHistory = history.stream().filter(m -> m.getRole() == AiMessage.Role.ASSISTANT).toList();
		var recentReplies = assistantHistory.stream().skip(Math.max(0, assistantHistory.size() - 4))
			.map(m -> AiInputPrivacy.redact(m.getContent().substring(0, Math.min(1000, m.getContent().length())))).toList();
		var facts = new ArrayList<Map<String, String>>();
		for (var fact : document.path("_analysis").path("facts")) facts.add(Map.of("kind", fact.path("kind").asText(),
			"value", AiInputPrivacy.redact(fact.path("value").asText()), "origin", fact.path("origin").asText()));
		// 회원 PK, 이메일, 닉네임, 다른 회원/프로젝트/포트폴리오를 조회하거나 전송하지 않는다.
		return AiInputPrivacy.redact(mapper.writeValueAsString(Map.of("latestUserMessage", latest,
			"userHistory", userHistory, "recentAssistantReplies", recentReplies,
			"currentConditions", documents.formal(document), "savedFacts", facts)));
	}
	private MessageAnalysisResponse response(AiConversation c, AiMessage user, AiMessage assistant, boolean replayed) {
		boolean ready = documents.missingRequiredQuestions(c.getCurrentConditions()).isEmpty();
		var metadata = documents.read(c.getCurrentConditions()).path("_analysis");
		return new MessageAnalysisResponse(MessageResponse.from(user), MessageResponse.from(assistant), c.getCurrentConditions(),
			metadata.isMissingNode() ? "{}" : mapper.writeValueAsString(metadata), ready, replayed);
	}
	private AiConversation owned(long id, long user) {
		if (id <= 0) throw new IllegalArgumentException("대화 ID를 확인해 주세요.");
		return conversations.findOwnedForUpdate(id, user).orElseThrow(AiMessageAnalysisService::missing);
	}
	private static String assistantKey(String key) {
		try { return "analysis:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
		catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
	}
	private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "메시지 또는 조건이 변경되었습니다. 최신 대화를 확인해 주세요."); }
	private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."); }
}
