package com.devmozip.backend.dto.message;
/** 커밋 이후 채팅/투표를 재조회하라는 신호. 본문/개인별 투표 내용은 전달하지 않는다. */
public record ProjectMembersChangedFrame(String type,String roomType,long roomId,long voteId,String status) {
	public ProjectMembersChangedFrame(long projectId,long voteId,String status){this("project.members.changed","project",projectId,voteId,status);}
}
