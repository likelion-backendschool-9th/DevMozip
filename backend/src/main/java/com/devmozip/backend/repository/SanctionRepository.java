package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.Sanction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회원 제재 내역 (docs/admin-plan.md D1). 유효한 제재는 revoked_at이 비어 있는 행이다. */
public interface SanctionRepository extends JpaRepository<Sanction, Long> {
	/** 회원의 유효한 최근 제재. 경고 안내(/api/v1/users/me)가 사유를 읽는다. */
	Optional<Sanction> findFirstByUser_IdAndRevokedAtIsNullOrderByIdDesc(Long userId);

	/** 회원의 유효한 제재 전체. 해제나 다른 제재로 바꿀 때 철회한다. */
	List<Sanction> findByUser_IdAndRevokedAtIsNull(Long userId);

	/** 회원별 가장 최근 제재(철회된 것 포함)와 처리 관리자. 회원 목록 한 페이지에 한 번 부른다. */
	@Query("""
		select s from Sanction s join fetch s.admin
		where s.id in (select max(x.id) from Sanction x where x.user.id in :userIds group by x.user.id)""")
	List<Sanction> findLatestByUserIds(@Param("userIds") Collection<Long> userIds);
}
