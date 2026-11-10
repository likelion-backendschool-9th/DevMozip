package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember extends BaseEntity {
	public enum Role { HOST, PARTICIPANT }
	public enum Status { ACTIVE, LEFT, KICKED }
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;
	@Column(name = "user_id", nullable = false)
	private long userId;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private Role role;
	@Column(name = "job_role", length = 50)
	private String jobRole;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private Status status;
	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;
	@Column(name = "left_at")
	private LocalDateTime leftAt;
	@Column(name = "end_requested_at")
	private LocalDateTime endRequestedAt;

	public void requestEnd(LocalDateTime now) { endRequestedAt = now; touch(now); }
	public void cancelEndRequest(LocalDateTime now) { endRequestedAt = null; touch(now); }

	public static ProjectMember participant(Project project,long userId,String jobRole,LocalDateTime now) {
		var member=new ProjectMember(project,userId,now);member.role=Role.PARTICIPANT;member.jobRole=jobRole;return member;
	}
	public void kick(LocalDateTime now){status=Status.KICKED;leftAt=now;endRequestedAt=null;touch(now);}
	public void leave(LocalDateTime now){status=Status.LEFT;leftAt=now;endRequestedAt=null;touch(now);}

	public static ProjectMember host(Project project, long userId, LocalDateTime now) {
		return new ProjectMember(project, userId, now);
	}
	private ProjectMember(Project project, long userId, LocalDateTime now) {
		super(now);
		this.project = project;
		this.userId = userId;
		this.role = Role.HOST;
		this.status = Status.ACTIVE;
		this.joinedAt = now;
	}
}
