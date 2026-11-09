package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="project_applications",uniqueConstraints=@UniqueConstraint(columnNames={"project_id","applicant_member_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ProjectApplication extends BaseEntity {
	public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id",nullable=false) private Project project;
	@Column(name="applicant_member_id",nullable=false) private long applicantMemberId;
	@Column(name="job_role",nullable=false,length=50) private String jobRole;
	@Column(columnDefinition="text") private String introduction;
	@Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
	@Column(name="rejection_reason",length=200) private String rejectionReason;
	@Column(name="applied_at",nullable=false) private LocalDateTime appliedAt;
	@Column(name="processed_at") private LocalDateTime processedAt;
	@Column(name="cancelled_at") private LocalDateTime cancelledAt;
	public ProjectApplication(Project project,long userId,String jobRole,String introduction,LocalDateTime now) {
		super(now);this.project=project;this.applicantMemberId=userId;this.jobRole=jobRole;this.introduction=introduction;this.status=Status.PENDING;this.appliedAt=now;
	}
	public void approve(LocalDateTime now){status=Status.APPROVED;processedAt=now;touch(now);}
	public void reject(String reason,LocalDateTime now){status=Status.REJECTED;rejectionReason=reason;processedAt=now;touch(now);}
	public void cancel(LocalDateTime now){status=Status.CANCELLED;cancelledAt=now;touch(now);}
}
