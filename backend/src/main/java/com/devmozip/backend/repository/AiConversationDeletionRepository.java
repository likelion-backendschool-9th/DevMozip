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
		// ERD column names are preserved; no recommendation entities/API are added.
		String requests = "SELECT id FROM ai_recommendation_requests WHERE ai_conversation_id = :conversationId "
				+ "OR ai_message_id IN (SELECT id FROM ai_messages WHERE conversation_id = :conversationId)";
		entityManager
				.createNativeQuery("DELETE FROM ai_recommendations WHERE recommended_request_id IN (" + requests + ")")
				.setParameter("conversationId", conversationId).executeUpdate();
		entityManager.createNativeQuery("DELETE FROM ai_recommendation_requests WHERE id IN (" + requests + ")")
				.setParameter("conversationId", conversationId).executeUpdate();
		entityManager.createNativeQuery("DELETE FROM ai_messages WHERE conversation_id = :conversationId")
				.setParameter("conversationId", conversationId).executeUpdate();
		// ai_usages is untouched. The service deletes the conversation last.
	}
}
