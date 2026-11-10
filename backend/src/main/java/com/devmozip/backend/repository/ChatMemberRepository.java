package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
	@Query("select c from ChatMember c where c.room.id = :roomId and c.member.id = :memberId and c.room.type = DIRECT")
	Optional<ChatMember> findDirectParticipant(@Param("roomId") Long roomId, @Param("memberId") Long memberId);

	// 방의 모든 참여 행. 나간 회원도 포함해 퇴장 문구와 이전 메시지의 작성자를 표시한다.
	@Query("select c from ChatMember c join fetch c.member where c.room.id = :roomId order by c.id")
	List<ChatMember> findAllInRoom(@Param("roomId") Long roomId);

	@Query("select c from ChatMember c join fetch c.member where c.room.id = :roomId and c.member.id = :memberId")
	Optional<ChatMember> findInRoom(@Param("roomId") Long roomId, @Param("memberId") Long memberId);

	// 방별 상대 참여자. 회원 정보를 함께 읽어 목록에서 방마다 조회하지 않는다.
	@Query("select c from ChatMember c join fetch c.member where c.room.id in :roomIds and c.member.id <> :memberId")
	List<ChatMember> findPeers(@Param("roomIds") Collection<Long> roomIds, @Param("memberId") Long memberId);

	// 읽음 위치는 앞으로만 옮긴다. 조건을 한 문장에 넣어 동시 요청이 위치를 되돌리지 못하게 한다.
	@Modifying
	@Query("""
		update ChatMember c set c.lastReadAt = :readAt
		where c.id = :id and (c.lastReadAt is null or c.lastReadAt < :readAt)""")
	int moveReadPosition(@Param("id") Long id, @Param("readAt") LocalDateTime readAt);
}
