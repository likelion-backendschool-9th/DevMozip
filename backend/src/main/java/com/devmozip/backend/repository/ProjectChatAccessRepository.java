package com.devmozip.backend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 프로젝트 채팅이 프로젝트 멤버를 읽는 조회. 쓰기 메서드가 없다.
 * 프로젝트 담당 코드(ProjectMemberRepository)를 바꾸지 않도록 메신저 쪽에 둔다.
 */
public interface ProjectChatAccessRepository extends Repository<ProjectMember, Long> {
	/** 프로젝트별 채팅 참여 인원 */
	interface ProjectMemberCount {
		Long getProjectId();
		long getMemberCount();
	}

	@Query("select pm from ProjectMember pm where pm.project.id = :projectId and pm.userId = :memberId")
	Optional<ProjectMember> findMembership(@Param("projectId") Long projectId, @Param("memberId") Long memberId);

	@Query("select pm from ProjectMember pm where pm.project.id = :projectId")
	List<ProjectMember> findByProject(@Param("projectId") Long projectId);

	// 내가 ACTIVE이고 모집 시작일이 지난 프로젝트. 방 행이 아직 없어도 포함하고, 삭제된 방과 내가 제외된 방은 뺀다.
	@Query("""
		select p.id from ProjectMember pm
		join pm.project p
		where pm.userId = :memberId and pm.status = ACTIVE
			and p.deletedAt is null and p.recruitmentStart <= :today and p.id < :cursor
			and lower(p.title) like :titlePattern escape '!'
			and not exists (select 1 from ChatRoom r where r.project = p and r.deletedAt is not null)
			and not exists (select 1 from ChatMember c where c.room.project = p and c.member.id = :memberId and c.leftAt is not null)
		order by p.id desc""")
	List<Long> findEnterableProjectIds(@Param("memberId") Long memberId, @Param("today") LocalDate today,
		@Param("cursor") Long cursor, @Param("titlePattern") String titlePattern, Limit limit);

	// 참여 행이 아직 없는 ACTIVE 멤버. users에 없는 회원은 행을 만들 수 없으므로 세지 않는다.
	@Query("""
		select count(pm) from ProjectMember pm
		where pm.project.id = :projectId and pm.status = ACTIVE
			and exists (select 1 from Member u where u.id = pm.userId)
			and not exists (select 1 from ChatMember c where c.room.id = :roomId and c.member.id = pm.userId)""")
	long countMissingChatMembers(@Param("projectId") Long projectId, @Param("roomId") Long roomId);

	// 프로젝트에서 ACTIVE가 아닌데 채팅에는 남아 있는 참여 행
	@Query("""
		select count(c) from ChatMember c
		where c.room.id = :roomId and c.leftAt is null
			and not exists (
				select 1 from ProjectMember pm
				where pm.project.id = :projectId and pm.userId = c.member.id and pm.status = ACTIVE)""")
	long countDepartedChatMembers(@Param("projectId") Long projectId, @Param("roomId") Long roomId);

	// ACTIVE 멤버 중 채팅에서 제외되지 않은 인원
	@Query("""
		select pm.project.id as projectId, count(pm) as memberCount
		from ProjectMember pm
		where pm.project.id in :projectIds and pm.status = ACTIVE
			and not exists (
				select 1 from ChatMember c
				where c.room.project.id = pm.project.id and c.member.id = pm.userId and c.leftAt is not null)
		group by pm.project.id""")
	List<ProjectMemberCount> countChatMembers(@Param("projectIds") Collection<Long> projectIds);
}
