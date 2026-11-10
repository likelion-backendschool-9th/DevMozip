package com.devmozip.backend.service.impl;

import com.devmozip.backend.service.ProjectLifecycleService;
import com.devmozip.backend.service.ProjectPeriodPolicy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.ProjectLifecycleChanged;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectLifecycleServiceImpl implements ProjectLifecycleService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectLifecycleRepository applications;
	private final ProjectCurrentUserProvider currentUser;
	private final ProjectPeriodPolicy periods;
	private final Clock clock;
	private final ApplicationEventPublisher events;
	public ProjectLifecycleServiceImpl(ProjectRepository projects, ProjectMemberRepository members,
		ProjectLifecycleRepository applications, ProjectCurrentUserProvider currentUser,
		ProjectPeriodPolicy periods, @Qualifier("projectClock") Clock clock, ApplicationEventPublisher events) {
		this.projects=projects; this.members=members; this.applications=applications; this.currentUser=currentUser;
		this.periods=periods; this.clock=clock; this.events=events;
	}
	@Override
	@Transactional
	public ProjectLifecycleResponse update(long id, ProjectLifecycleRequest request) {
		if (id<=0 || request==null || request.action()==null) throw bad("프로젝트 ID와 명령을 확인해 주세요.");
		long userId = currentUser.getCurrentUserId();
		Project project = projects.findLiveForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
		ProjectMember actor = members.findByProjectIdAndUserIdAndStatus(id, userId, ProjectMember.Status.ACTIVE)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 프로젝트 팀원만 요청할 수 있습니다."));
		validateShape(request);
		if (project.getStatus()==ProjectStatus.COMPLETED) throw conflict("종료된 프로젝트는 변경할 수 없습니다.");
		LocalDateTime now = LocalDateTime.now(clock);
		ProjectStatus previous = project.getStatus();
		LocalDate oldRecruitmentEnd = project.getRecruitmentEnd(), oldDevelopmentEnd = project.getDevelopmentEnd();
		ProjectLifecycleChanged.Type eventType = null;
		switch (request.action()) {
			case CHANGE_STATUS -> {
				requireHost(project, actor);
				changeStatus(project, request.status(), now);
				eventType = ProjectLifecycleChanged.Type.STATUS_CHANGED;
			}
			case EXTEND_PERIOD -> {
				requireHost(project, actor);
				extend(project, request, now);
				eventType = ProjectLifecycleChanged.Type.PERIOD_EXTENDED;
			}
			case REQUEST_END -> {
				requireInProgress(project);
				if (actor.getEndRequestedAt()!=null) throw conflict("이미 종료를 요청했습니다.");
				actor.requestEnd(now); members.flush();
				long count = members.countByProjectIdAndStatus(id, ProjectMember.Status.ACTIVE);
				long requested = members.countByProjectIdAndStatusAndEndRequestedAtIsNotNull(id, ProjectMember.Status.ACTIVE);
				if (requested > count/2) {
					project.changeStatus(ProjectStatus.COMPLETED, now);
					eventType = ProjectLifecycleChanged.Type.COMPLETED;
				}
			}
			case CANCEL_END_REQUEST -> {
				requireInProgress(project);
				if (actor.getEndRequestedAt()==null) throw conflict("취소할 종료 요청이 없습니다.");
				actor.cancelEndRequest(now);
			}
		}
		projects.flush(); members.flush();
		if (eventType!=null) events.publishEvent(new ProjectLifecycleChanged(eventType, id, userId, previous, project.getStatus(),
			oldRecruitmentEnd, project.getRecruitmentEnd(), oldDevelopmentEnd, project.getDevelopmentEnd(), now));
		long count = members.countByProjectIdAndStatus(id, ProjectMember.Status.ACTIVE);
		return new ProjectLifecycleResponse(ProjectResponse.from(project), count,
			members.countByProjectIdAndStatusAndEndRequestedAtIsNotNull(id, ProjectMember.Status.ACTIVE), count/2+1, actor.getEndRequestedAt()!=null);
	}
	private void changeStatus(Project project, ProjectStatus target, LocalDateTime now) {
		ProjectStatus current = project.getStatus();
		boolean allowed = current==ProjectStatus.RECRUITING && target==ProjectStatus.RECRUITMENT_COMPLETED
			|| current==ProjectStatus.RECRUITMENT_COMPLETED && target==ProjectStatus.IN_PROGRESS;
		if (current==ProjectStatus.RECRUITMENT_COMPLETED && target==ProjectStatus.RECRUITING) {
			LocalDate today = LocalDate.now(clock);
			allowed = !today.isBefore(project.getRecruitmentStart()) && !today.isAfter(project.getRecruitmentEnd());
		}
		if (!allowed) throw conflict("허용되지 않는 상태 변경입니다. 종료는 팀원 과반수의 요청이 필요합니다.");
		if (target==ProjectStatus.IN_PROGRESS && applications.hasPendingApplications(project.getId()))
			throw conflict("대기 지원서를 처리한 뒤 프로젝트를 시작해 주세요.");
		project.changeStatus(target, now);
	}
	private void extend(Project project, ProjectLifecycleRequest request, LocalDateTime now) {
		LocalDate recruitmentEnd = request.recruitmentEnd()==null ? project.getRecruitmentEnd() : request.recruitmentEnd();
		LocalDate developmentEnd = request.developmentEnd()==null ? project.getDevelopmentEnd() : request.developmentEnd();
		if (recruitmentEnd.equals(project.getRecruitmentEnd()) && developmentEnd.equals(project.getDevelopmentEnd())) throw conflict("하나 이상의 종료일을 연장해 주세요.");
		periods.validateUpdate(project, new CreateProjectRequest(project.getTitle(), project.getSummary(), project.getDescription(), project.getGoal(),
			project.getImageUrl(), project.isPublic(), project.getRecruitmentSlots().stream().map(s -> new CreateProjectRequest.RecruitmentSlot(s.roleCode(),s.capacity(),s.minimumYears())).toList(),
			project.getTechStacks(), project.getInterests(), project.getRecruitmentStart(), recruitmentEnd, project.getDevelopmentStart(), developmentEnd));
		project.extendPeriods(recruitmentEnd, developmentEnd, now);
	}
	private void validateShape(ProjectLifecycleRequest request) {
		boolean status = request.status()!=null, dates = request.recruitmentEnd()!=null || request.developmentEnd()!=null;
		switch (request.action()) {
			case CHANGE_STATUS -> { if (!status || dates) throw bad("상태 변경에는 status만 입력해 주세요."); }
			case EXTEND_PERIOD -> { if (status || !dates) throw bad("기간 연장에는 종료일을 하나 이상 입력해 주세요."); }
			case REQUEST_END, CANCEL_END_REQUEST -> { if (status || dates) throw bad("종료 요청·취소에는 다른 변경 값을 넣을 수 없습니다."); }
		}
	}
	private void requireHost(Project project, ProjectMember actor) {
		if (project.getHostMemberId()!=actor.getUserId() || actor.getRole()!=ProjectMember.Role.HOST)
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "주최자만 상태와 기간을 변경할 수 있습니다.");
	}
	private void requireInProgress(Project project) { if (project.getStatus()!=ProjectStatus.IN_PROGRESS) throw conflict("진행 중인 프로젝트에서만 종료 요청·취소가 가능합니다."); }
	private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
	private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }
}
