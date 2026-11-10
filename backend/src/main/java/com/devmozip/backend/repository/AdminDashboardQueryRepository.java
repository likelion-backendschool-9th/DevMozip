package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.entity.Sanction;
import com.devmozip.backend.entity.enums.PostVisibility;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 관리자 대시보드 집계 (WBS 14.1, docs/admin-plan.md 5.2). 읽기만 한다. */
public interface AdminDashboardQueryRepository extends Repository<Member, Long> {
	/** 탈퇴하지 않은 회원 수 (#135의 탈퇴 판정과 같다) */
	@Query("select count(m) from Member m where not (m.email is null and m.status is null)")
	long countMembers();

	@Query("select count(m) from Member m where m.status = :status")
	long countMembersByStatus(@Param("status") Member.MemberStatus status);

	/** 이용 기간 안의 구독이 있는 회원 수 */
	@Query("select count(distinct s.member.id) from Subscription s where s.status in :statuses")
	long countSubscribers(@Param("statuses") Collection<SubscriptionStatus> statuses);

	@Query("select count(p) from Project p where p.deletedAt is null")
	long countLiveProjects();

	@Query("select count(p) from Post p where p.deletedAt is null and p.visibility = :visibility")
	long countLivePosts(@Param("visibility") PostVisibility visibility);

	@Query("select count(r) from Report r where r.status in :statuses")
	long countReports(@Param("statuses") Collection<Report.Status> statuses);

	/** 최근 제재 시작 기록 (D7) */
	@Query("select s from Sanction s join fetch s.admin join fetch s.user order by s.startsAt desc, s.id desc")
	List<Sanction> recentSanctions(Pageable pageable);

	/** 최근 제재 철회 기록 (D7). 해제나 다른 제재로 바뀌어 revoked_at이 채워진 제재다. */
	@Query("select s from Sanction s join fetch s.user where s.revokedAt is not null order by s.revokedAt desc, s.id desc")
	List<Sanction> recentRevocations(Pageable pageable);

	/** 최근 신고 처리 기록 (D7). 처리 시각이 있는 신고만이다. */
	@Query("select r from Report r where r.handledAt is not null order by r.handledAt desc, r.id desc")
	List<Report> recentHandledReports(Pageable pageable);
}
