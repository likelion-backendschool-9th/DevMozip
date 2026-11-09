package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiConversation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {
	List<AiConversation> findByUserIdOrderByCreatedAtDescIdDesc(long userId);

	Optional<AiConversation> findByIdAndUserId(long id, long userId);

	// Development identity check against users, not ai_conversations.
	@Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE id = :userId)", nativeQuery = true)
	boolean existsUserById(@Param("userId") long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from AiConversation c where c.id = :id and c.userId = :userId")
	Optional<AiConversation> findOwnedForUpdate(@Param("id") long id, @Param("userId") long userId);
}
