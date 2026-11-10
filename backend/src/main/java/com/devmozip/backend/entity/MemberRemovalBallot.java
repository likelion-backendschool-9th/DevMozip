package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="member_removal_ballots",uniqueConstraints=@UniqueConstraint(columnNames={"vote_id","voter_member_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MemberRemovalBallot extends BaseEntity {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="vote_id") private MemberRemovalVote vote;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="voter_member_id") private ProjectMember voter;
	private Boolean agree;
	@Column(name="voted_at") private LocalDateTime votedAt;
	public MemberRemovalBallot(MemberRemovalVote vote,ProjectMember voter,LocalDateTime now){super(now);this.vote=vote;this.voter=voter;}
	public void cast(boolean agree,LocalDateTime now){this.agree=agree;votedAt=now;touch(now);}
}
