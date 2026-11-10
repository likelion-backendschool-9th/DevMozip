package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.ProjectReportReason;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectReportServiceImpl implements ProjectReportService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final MemberRepository users;
	private final ReportRepository reports;
	private final ProjectCurrentUserProvider currentUser;
	private final Clock clock;
	public ProjectReportServiceImpl(ProjectRepository projects,ProjectMemberRepository members,MemberRepository users,ReportRepository reports,ProjectCurrentUserProvider currentUser,@Qualifier("projectClock") Clock clock){
		this.projects=projects;this.members=members;this.users=users;this.reports=reports;this.currentUser=currentUser;this.clock=clock;
	}
	@Override @Transactional
	public ProjectReportResponse report(long projectId,CreateProjectReportRequest request){
		long userId=currentUser.getCurrentUserId();
		if(projectId<=0||request==null||!ProjectReportReason.contains(request.reason())||(request.detail()!=null&&request.detail().length()>1000))throw error(HttpStatus.BAD_REQUEST,"신고 사유와 내용을 확인해 주세요.");
		// 같은 공고의 동시 신고와 삭제를 직렬화한다. 사용자 신고 기록은 중복 판정에 포함하지 않는다.
		var project=projects.findLiveForUpdate(projectId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"프로젝트가 없거나 접근할 수 없습니다."));
		if(!project.isPubliclyVisible()&&!members.existsByProjectIdAndUserIdAndStatus(projectId,userId,ProjectMember.Status.ACTIVE))throw error(HttpStatus.NOT_FOUND,"프로젝트가 없거나 접근할 수 없습니다.");
		if(project.getHostMemberId()==userId)throw error(HttpStatus.BAD_REQUEST,"본인의 프로젝트는 신고할 수 없습니다.");
		if(reports.existsByReporter_IdAndProjectIdAndReportedUserIsNullAndPostIdIsNullAndCommentIdIsNull(userId,projectId))throw error(HttpStatus.CONFLICT,"이미 신고한 프로젝트입니다.");
		String detail=request.detail()==null?"":request.detail().trim();
		var report=Report.ofProject(users.getReferenceById(userId),projectId,request.reason()+(detail.isEmpty()?"":"\n\n"+detail),LocalDateTime.now(clock));
		reports.saveAndFlush(report);
		return new ProjectReportResponse(report.getId(),projectId,report.getStatus().name(),report.getCreatedAt());
	}
	private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
