package com.devmozip.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "ai_recommendations", uniqueConstraints = {
	@UniqueConstraint(name = "uk_ai_result_user", columnNames = {"recommended_request_id", "recommended_user_id"}),
	@UniqueConstraint(name = "uk_ai_result_project", columnNames = {"recommended_request_id", "recommended_project_id"})})
@Check(constraints = "((recommended_user_id IS NOT NULL AND recommended_project_id IS NULL) OR (recommended_user_id IS NULL AND recommended_project_id IS NOT NULL)) AND match_score BETWEEN 0 AND 100 AND rank >= 1")
public class AiRecommendation {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recommended_request_id", nullable = false)
	private AiRecommendationRequest request;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recommended_user_id")
	private Member user;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recommended_project_id")
	private Project project;
	@Column(name = "match_score", nullable = false, precision = 5, scale = 2)
	private BigDecimal matchScore;
	@Column(name = "basic_reason", nullable = false, columnDefinition = "text")
	private String basicReason;
	@Column(name = "detailed_reason", columnDefinition = "text")
	private String detailedReason;
	@Column(nullable = false)
	private int rank;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected AiRecommendation() {}
	public AiRecommendation(AiRecommendationRequest request, Member user, Project project,
		BigDecimal score, String reason, int rank) {
		if ((user == null) == (project == null) || score == null || score.signum() < 0
			|| score.compareTo(new BigDecimal("100")) > 0 || rank < 1 || reason == null) {
			throw new IllegalArgumentException("추천 결과 무결성 오류입니다.");
		}
		this.request = request;
		this.user = user;
		this.project = project;
		this.matchScore = score;
		this.basicReason = reason;
		this.rank = rank;
		this.createdAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
	}
	public Long getId() { return id; }
	public Member getUser() { return user; }
	public Long getUserId() { return user == null ? null : user.getId(); }
	public Long getProjectId() { return project == null ? null : project.getId(); }
	public BigDecimal getMatchScore() { return matchScore; }
	public String getBasicReason() { return basicReason; }
	public int getRank() { return rank; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
