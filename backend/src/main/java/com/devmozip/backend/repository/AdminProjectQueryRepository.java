package com.devmozip.backend.repository;

import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 공고 목록 (WBS 14.4). 삭제된 공고는 뺀다. 비공개·관리자 숨김 공고는 포함한다.
 * 검색어는 AdminPaging.keyword로 소문자·이스케이프한 제목 검색어다.
 */
public interface AdminProjectQueryRepository extends Repository<Project, Long> {
	@Query("""
		select p from Project p
		where p.deletedAt is null
		and (:q is null or lower(p.title) like :q escape '\\')
		and (:status is null or p.status = :status)
		and (:restricted is null or p.isRestricted = :restricted)
		order by p.id desc""")
	Page<Project> search(@Param("q") String q, @Param("status") ProjectStatus status, @Param("restricted") Boolean restricted,
		Pageable pageable);
}
