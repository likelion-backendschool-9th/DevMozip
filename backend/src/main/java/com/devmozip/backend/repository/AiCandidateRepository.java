package com.devmozip.backend.repository;

import com.devmozip.backend.dto.ai.internal.DeveloperCandidate;
import com.devmozip.backend.dto.ai.internal.SimilarProjectCandidate;
import com.devmozip.backend.entity.Member;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 기존 도메인 저장소·엔티티를 변경하지 않는 AI 전용 읽기 조회다. */
@Repository
@Transactional(readOnly = true)
public class AiCandidateRepository {
	private final EntityManager entityManager;

	public AiCandidateRepository(EntityManager entityManager) { this.entityManager = entityManager; }

	public List<DeveloperCandidate> developers(long requesterId, long afterId, int batchSize) {
		return entityManager.createQuery("""
			select new com.devmozip.backend.dto.ai.internal.DeveloperCandidate(
				m.id, m.nickname, m.tag, m.jobCategories, m.skills, m.interests, m.career, m.trustScore, m.summary)
			from Member m where m.id <> :requester and m.id > :afterId
				and m.status = :status and m.collaborationStatus = :available
				and m.tag is not null and trim(m.tag) <> ''
			order by m.id asc
			""", DeveloperCandidate.class)
			.setParameter("requester", requesterId).setParameter("afterId", afterId)
			.setParameter("status", Member.MemberStatus.ACTIVE)
			.setParameter("available", Member.CollaborationStatus.AVAILABLE)
			.setMaxResults(batchSize).getResultList();
	}

	public List<SimilarProjectCandidate> projects(long afterId, int batchSize) {
		return entityManager.createQuery("""
			select new com.devmozip.backend.dto.ai.internal.SimilarProjectCandidate(
				p.id, p.title, p.description, p.techStacks, p.interests, p.status, p.developmentStart, p.developmentEnd)
			from Project p where p.id > :afterId and p.isPublic = true and p.deletedAt is null
			order by p.id asc
			""", SimilarProjectCandidate.class)
			.setParameter("afterId", afterId).setMaxResults(batchSize).getResultList();
	}
}
