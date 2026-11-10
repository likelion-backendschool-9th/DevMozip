package com.devmozip.backend.dto.project;
import java.time.LocalDateTime;
public record MemberRemovalVoteResponse(long id,long projectId,long targetMemberId,String targetNickname,String reason,String status,
	int yes,int no,int eligibleVoterCount,int requiredCount,Boolean myVote,boolean canVote,LocalDateTime createdAt,LocalDateTime expiresAt,LocalDateTime resolvedAt) {}
