package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.ChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
	// 전송을 방마다 한 줄로 세워 메시지 ID와 sent_at이 커밋 순서대로 늘어나게 한다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ChatRoom r where r.id = :id")
	Optional<ChatRoom> findForSendById(@Param("id") Long id);

	// 두 회원이 함께 참여한 1:1 방. 호출 전에 두 회원 행을 잠가야 동시 요청에도 방이 하나만 생긴다.
	@Query("""
		select r.id from ChatRoom r
		where r.type = DIRECT
			and exists (select 1 from ChatMember c where c.room = r and c.member.id = :firstMemberId)
			and exists (select 1 from ChatMember c where c.room = r and c.member.id = :secondMemberId)
		order by r.id""")
	List<Long> findDirectRoomIds(@Param("firstMemberId") Long firstMemberId, @Param("secondMemberId") Long secondMemberId);

	// 프로젝트당 방은 하나다(project_id UNIQUE). 삭제된 방도 돌려준다.
	@Query("select r from ChatRoom r where r.type = PROJECT and r.project.id = :projectId")
	Optional<ChatRoom> findProjectRoom(@Param("projectId") Long projectId);

	// 전송·삭제·제외를 방마다 한 줄로 세운다. 다른 조회보다 먼저 불러야 잠근 뒤의 값을 읽는다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ChatRoom r where r.type = PROJECT and r.project.id = :projectId")
	Optional<ChatRoom> findProjectRoomForUpdate(@Param("projectId") Long projectId);
}
