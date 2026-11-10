package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import com.devmozip.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림 쓰기와 본인 알림 단건 조회. 목록과 안 읽음 수는 NotificationQueryRepository가 맡는다. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
	/** 다른 사람의 알림은 없는 알림과 같이 비어 있다. */
	Optional<Notification> findByIdAndRecipientId(long id, long recipientId);

	boolean existsByRecipientIdAndToken(long recipientId, String token);

	/** 본인의 안 읽은 알림을 모두 읽음으로 바꾼다. 이미 읽은 알림의 읽은 시각은 그대로다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipient.id = :recipientId AND n.readAt IS NULL")
	int markAllRead(@Param("recipientId") long recipientId, @Param("now") LocalDateTime now);
}
