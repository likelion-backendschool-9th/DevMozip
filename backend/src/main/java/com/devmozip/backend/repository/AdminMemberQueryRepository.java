package com.devmozip.backend.repository;

import java.util.Optional;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 회원 조회·제재 (WBS 14.2·14.3). 탈퇴 회원(#135, email·status가 NULL)은 목록과 제재 대상에서 뺀다.
 * 검색어는 AdminPaging.keyword로 소문자·이스케이프한 값이다.
 */
public interface AdminMemberQueryRepository extends Repository<Member, Long> {
	@Query("""
		select m from Member m
		where not (m.email is null and m.status is null)
		and (:q is null or lower(m.nickname) like :q escape '\\' or lower(m.email) like :q escape '\\'
			or lower(m.tag) like :q escape '\\')
		and (:status is null or m.status = :status)
		and (:role is null or m.role = :role)
		order by m.id desc""")
	Page<Member> search(@Param("q") String q, @Param("status") MemberStatus status, @Param("role") MemberRole role,
		Pageable pageable);

	/** 제재할 회원을 잠근다. 로그인·탈퇴가 잡는 같은 회원 행이라 서로 하나씩 처리된다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.tag = :tag and not (m.email is null and m.status is null)")
	Optional<Member> findForSanctionByTag(@Param("tag") String tag);
}
