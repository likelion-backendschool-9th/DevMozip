package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="member_removal_votes") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MemberRemovalVote extends BaseEntity {
	public enum Status { ACTIVE, PASSED, REJECTED, EXPIRED, CANCELLED }
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id") private Project project;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="proposer_member_id") private ProjectMember proposer;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="target_member_id") private ProjectMember target;
	@Column(nullable=false,columnDefinition="text") private String reason;
	@Column(name="eligible_voter_count",nullable=false) private int eligibleVoterCount;
	@Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
	@Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
	@Column(name="resolved_at") private LocalDateTime resolvedAt;
	public MemberRemovalVote(Project p,ProjectMember proposer,ProjectMember target,String reason,int voters,LocalDateTime now){
		super(now);project=p;this.proposer=proposer;this.target=target;this.reason=reason;eligibleVoterCount=voters;status=Status.ACTIVE;expiresAt=now.plusHours(72);
	}
	public void resolve(Status result,LocalDateTime now){status=result;resolvedAt=now;touch(now);}
}
