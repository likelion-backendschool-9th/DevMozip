package com.devmozip.backend.entity;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="reviews",uniqueConstraints=@UniqueConstraint(columnNames={"project_id","reviewer_member_id","reviewee_member_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ProjectReview extends BaseEntity {
	public enum Status { PENDING, SUBMITTED }
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id",nullable=false) private Project project;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reviewer_member_id",nullable=false) private ProjectMember reviewer;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reviewee_member_id",nullable=false) private ProjectMember reviewee;
	@Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
	@Column(name="token_hash",nullable=false,length=255) private String tokenHash;
	@Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
	private Integer rating;
	@Column(name="submitted_at") private LocalDateTime submittedAt;
	@Column(name="last_reminded_at") private LocalDateTime lastRemindedAt;
	public ProjectReview(Project p,ProjectMember author,ProjectMember target,String hash,LocalDateTime now){super(now);project=p;reviewer=author;reviewee=target;tokenHash=hash;expiresAt=p.getCompletedAt().plusDays(30);status=Status.PENDING;}
	public void rotate(String hash,LocalDateTime now){tokenHash=hash;touch(now);}
	public void remind(LocalDateTime now){lastRemindedAt=now;touch(now);}
	public void submit(int stars,LocalDateTime now){rating=stars;status=Status.SUBMITTED;submittedAt=now;touch(now);}
}
