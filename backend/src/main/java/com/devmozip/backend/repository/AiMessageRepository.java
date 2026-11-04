package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiMessage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
	List<AiMessage> findByConversationIdOrderByCreatedAtAscIdAsc(long conversationId);
}
