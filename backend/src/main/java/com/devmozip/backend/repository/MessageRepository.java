package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.Message;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
	/** 방별 안 읽은 수 조회 결과 */
	interface RoomUnreadCount {
		Long getRoomId();
		long getUnreadCount();
	}

	/** 프로젝트별 안 읽은 수 조회 결과 */
	interface ProjectUnreadCount {
		Long getProjectId();
		long getUnreadCount();
	}

	/** 참여 행별 읽음 위치(그 시각까지의 마지막 메시지 ID) */
	interface ReadPosition {
		Long getChatMemberId();
		Long getLastReadMessageId();
	}

	// 내 1:1 방별 마지막 메시지 ID. 메시지가 없는 방은 나오지 않고, 최근 대화부터 정렬한다.
	@Query("""
		select max(m.id) from Message m
		join m.chatMember sender
		join ChatMember mine on mine.room = sender.room
		where mine.member.id = :memberId
			and mine.room.type = DIRECT
			and m.deletedAt is null
			and exists (
				select 1 from ChatMember peer
				where peer.room = mine.room and peer.member.id <> :memberId
					and lower(coalesce(peer.member.nickname, '')) like :nicknamePattern escape '!')
		group by mine.room.id
		having max(m.id) < :cursor
		order by max(m.id) desc""")
	List<Long> findLastMessageIds(@Param("memberId") Long memberId, @Param("nicknamePattern") String nicknamePattern,
		@Param("cursor") Long cursor, Limit limit);

	@Query("select m from Message m join fetch m.chatMember c join fetch c.room where m.id in :ids")
	List<Message> findWithRoomByIdIn(@Param("ids") Collection<Long> ids);

	@Query("""
		select mine.room.id as roomId, count(m) as unreadCount
		from ChatMember mine
		join ChatMember sender on sender.room = mine.room and sender.id <> mine.id
		join Message m on m.chatMember = sender
		where mine.member.id = :memberId and mine.room.id in :roomIds
			and m.deletedAt is null
			and (mine.lastReadAt is null or m.sentAt > mine.lastReadAt)
		group by mine.room.id""")
	List<RoomUnreadCount> countUnreadByRoom(@Param("memberId") Long memberId, @Param("roomIds") Collection<Long> roomIds);

	@Query("""
		select count(m)
		from ChatMember mine
		join ChatMember sender on sender.room = mine.room and sender.id <> mine.id
		join Message m on m.chatMember = sender
		where mine.member.id = :memberId
			and mine.room.type = DIRECT
			and m.deletedAt is null
			and (mine.lastReadAt is null or m.sentAt > mine.lastReadAt)""")
	long countUnreadDirect(@Param("memberId") Long memberId);

	@Query("select m from Message m where m.chatMember.room.id = :roomId and m.deletedAt is null order by m.id desc")
	List<Message> findLatest(@Param("roomId") Long roomId, Limit limit);

	@Query("select m from Message m where m.chatMember.room.id = :roomId and m.deletedAt is null and m.id < :before order by m.id desc")
	List<Message> findBefore(@Param("roomId") Long roomId, @Param("before") Long before, Limit limit);

	@Query("select m from Message m where m.chatMember.room.id = :roomId and m.deletedAt is null and m.id > :after order by m.id asc")
	List<Message> findAfter(@Param("roomId") Long roomId, @Param("after") Long after, Limit limit);

	@Query("select m from Message m where m.id = :id and m.chatMember.room.id = :roomId and m.deletedAt is null")
	Optional<Message> findInRoom(@Param("id") Long id, @Param("roomId") Long roomId);

	// 삭제된 메시지도 포함한다. 새 메시지는 지금까지의 모든 메시지보다 늦어야 한다.
	@Query("select max(m.sentAt) from Message m where m.chatMember.room.id = :roomId")
	Optional<LocalDateTime> findLatestSentAt(@Param("roomId") Long roomId);

	// 읽음 위치(시각)까지의 마지막 메시지 ID. 읽은 메시지가 없으면 null이다.
	@Query("select max(m.id) from Message m where m.chatMember.room.id = :roomId and m.deletedAt is null and m.sentAt <= :readAt")
	Long findLastIdSentUntil(@Param("roomId") Long roomId, @Param("readAt") LocalDateTime readAt);

	// 프로젝트 방의 참여자별 읽음 위치를 한 번에 구한다. 아직 읽지 않은 참여자는 나오지 않는다.
	@Query("""
		select c.id as chatMemberId,
			(select max(m.id) from Message m
				where m.chatMember.room = c.room and m.deletedAt is null and m.sentAt <= c.lastReadAt) as lastReadMessageId
		from ChatMember c
		where c.room.id = :roomId and c.lastReadAt is not null""")
	List<ReadPosition> findReadPositions(@Param("roomId") Long roomId);

	// 나간 방과 삭제된 방은 세지 않는다. 승인 전에 오간 메시지도 내가 보내지 않았으면 센다.
	@Query("""
		select r.project.id as projectId, count(m) as unreadCount
		from ChatMember mine
		join mine.room r
		join ChatMember sender on sender.room = r and sender.id <> mine.id
		join Message m on m.chatMember = sender
		where mine.member.id = :memberId and r.type = PROJECT and r.project.id in :projectIds
			and r.deletedAt is null and mine.leftAt is null and m.deletedAt is null
			and (mine.lastReadAt is null or m.sentAt > mine.lastReadAt)
		group by r.project.id""")
	List<ProjectUnreadCount> countUnreadByProject(@Param("memberId") Long memberId, @Param("projectIds") Collection<Long> projectIds);

	// 7.21 합계. 프로젝트 멤버가 ACTIVE이고 삭제되지 않은 프로젝트의 방만 센다.
	@Query("""
		select count(m)
		from ChatMember mine
		join mine.room r
		join r.project p
		join ChatMember sender on sender.room = r and sender.id <> mine.id
		join Message m on m.chatMember = sender
		where mine.member.id = :memberId and r.type = PROJECT
			and r.deletedAt is null and p.deletedAt is null and mine.leftAt is null and m.deletedAt is null
			and (mine.lastReadAt is null or m.sentAt > mine.lastReadAt)
			and exists (select 1 from ProjectMember pm where pm.project = p and pm.userId = :memberId and pm.status = ACTIVE)""")
	long countUnreadProject(@Param("memberId") Long memberId);
}
