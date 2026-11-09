package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공통 신고. 컬럼은 프로젝트 청사진의 reports를 따르고, 대상 컬럼 중 신고 종류에 맞는 것만 채운다. */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {
	public enum Status { RECEIVED, REVIEWING, RESOLVED, REJECTED }
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id", nullable = false)
	private Member reporter;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reported_user_id")
	private Member reportedUser;
	@Column(name = "project_id")
	private Long projectId;
	@Column(name = "post_id")
	private Long postId;
	@Column(name = "comment_id")
	private Long commentId;
	@Column(name = "handled_by")
	private Long handledBy;
	@Column(nullable = false, columnDefinition = "TEXT")
	private String reason;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private Status status;
	@Column(columnDefinition = "TEXT")
	private String resolution;
	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	public static Report ofUser(Member reporter, Member reportedUser, String reason, LocalDateTime now) {
		return new Report(reporter, reportedUser, reason, now);
	}
	public static Report ofProject(Member reporter, long projectId, String reason, LocalDateTime now) {
		Report report = new Report(reporter, null, reason, now);
		report.projectId = projectId;
		return report;
	}
	private Report(Member reporter, Member reportedUser, String reason, LocalDateTime now) {
		super(now);
		this.reporter = reporter;
		this.reportedUser = reportedUser;
		this.reason = reason;
		this.status = Status.RECEIVED;
	}
}
