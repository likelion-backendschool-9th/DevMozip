package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiMessage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
	java.util.Optional<AiMessage> findByConversationIdAndClientKey(long conversationId, String clientKey);

	java.util.Optional<AiMessage> findByIdAndConversationId(long id, long conversationId);

	List<AiMessage> findByConversationIdOrderByCreatedAtAscIdAsc(long conversationId);
}
