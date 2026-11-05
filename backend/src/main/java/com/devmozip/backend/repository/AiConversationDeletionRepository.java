package com.devmozip.backend.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class AiConversationDeletionRepository {
	private final EntityManager entityManager;

	public AiConversationDeletionRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public void deleteChildren(long conversationId) {
		entityManager.createNativeQuery("DELETE FROM ai_messages WHERE conversation_id = :conversationId")
				.setParameter("conversationId", conversationId).executeUpdate();
		// Stage 1 deletes only messages. The service deletes the conversation in the same transaction.
	}
}
