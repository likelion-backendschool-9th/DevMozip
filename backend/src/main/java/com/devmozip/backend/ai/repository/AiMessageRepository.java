package com.devmozip.backend.ai.repository;

import com.devmozip.backend.ai.entity.AiMessage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
	List<AiMessage> findByConversationIdOrderByCreatedAtAscIdAsc(long conversationId);
}
