package com.devmozip.backend.service;

import java.time.*;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.UserReportReason;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class ProjectReportManagementService {
	private final ProjectRepository projects;private final ProjectMemberRepository team;private final MemberRepository users;private final ReportRepository reports;private final ProjectCurrentUserProvider identity;private final Clock clock;
	public ProjectReportManagementService(ProjectRepository projects,ProjectMemberRepository team,MemberRepository users,ReportRepository reports,ProjectCurrentUserProvider identity,@Qualifier("projectClock") Clock clock){this.projects=projects;this.team=team;this.users=users;this.reports=reports;this.identity=identity;this.clock=clock;}
	public ProjectReportResponse reportMember(long projectId,long memberId,CreateProjectMemberReportRequest request){
		long user=identity.getCurrentUserId();
		projects.findLiveForUpdate(projectId).orElseThrow(()->error(404,"프로젝트가 없습니다."));
		if(!team.existsByProjectIdAndUserIdAndStatus(projectId,user,ProjectMember.Status.ACTIVE))throw error(403,"현재 팀원만 프로젝트 팀원을 신고할 수 있습니다.");
		var target=team.findByProjectId(projectId).stream().filter(m->m.getId()==memberId).findFirst().orElseThrow(()->error(404,"해당 프로젝트의 참여자가 아닙니다."));
		if(target.getUserId()==user)throw error(400,"본인은 신고할 수 없습니다.");
		if(!UserReportReason.contains(request.reason()))throw error(400,"신고 사유를 확인해 주세요.");
		if(reports.existsByReporter_IdAndProjectIdAndReportedUser_IdAndPostIdIsNullAndCommentIdIsNull(user,projectId,target.getUserId()))throw error(409,"이 프로젝트에서 이미 신고한 팀원입니다.");
		var detail=request.detail()==null?"":request.detail().strip();
		var saved=reports.saveAndFlush(Report.ofProjectMember(users.getReferenceById(user),users.getReferenceById(target.getUserId()),projectId,request.reason()+(detail.isEmpty()?"":"\n\n"+detail),LocalDateTime.now(clock)));
		return new ProjectReportResponse(saved.getId(),projectId,saved.getStatus().name(),saved.getCreatedAt());
	}
	// 관리자 목록·처리는 AdminReportService(/api/v1/admin/reports)로 옮겼다 (docs/admin-plan.md D5).
	private ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}
