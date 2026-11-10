package com.devmozip.backend.dto.project;

import java.util.List;

/** 종료 동의 정보는 인증된 현재 팀원 본인에게만 제공한다. */
public record ProjectDetailResponse(ProjectResponse project,long memberCount,boolean recruitmentClosed,Membership membership,Host host,List<TeamMember> team) {
	public record TeamMember(long userId,String tag,String nickname,String role,String jobRole,String status) {}
	public record Host(long id,String tag,String nickname) {}
	public record Membership(String myRole,boolean myEndRequested,long endRequestCount,long requiredEndRequestCount) {}
}
