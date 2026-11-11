package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectQueryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProjectQueryServiceImpl implements ProjectQueryService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectQueryRepository queries;
	private final ProjectCurrentUserProvider currentUser;
	private final Clock clock;
	public ProjectQueryServiceImpl(ProjectRepository projects, ProjectMemberRepository members, ProjectQueryRepository queries,
		ProjectCurrentUserProvider currentUser, @Qualifier("projectClock") Clock clock) {
		this.projects=projects; this.members=members; this.queries=queries; this.currentUser=currentUser; this.clock=clock;
	}
	@Override public ProjectPageResponse search(ProjectSearchRequest request) { return page(request, null, null); }
	@Override public ProjectPageResponse mine(ProjectSearchRequest request, MyProjectsTab tab) {
		return page(request, currentUser.getCurrentUserId(), tab);
	}
	private ProjectPageResponse page(ProjectSearchRequest request, Long userId, MyProjectsTab tab) {
		LocalDate today = LocalDate.now(clock);
		var result = queries.search(request, today, userId, tab);
		var byId = projects.findAllById(result.rows().stream().map(ProjectQueryRepository.Row::id).toList()).stream()
			.collect(Collectors.toMap(Project::getId, Function.identity()));
		var items = result.rows().stream().map(row -> ProjectListItemResponse.from(byId.get(row.id()), row.memberCount(), row.myRole(), today)).toList();
		int size = request.effectiveSize();
		return new ProjectPageResponse(items, result.total(), request.effectivePage(), size, (result.total()+size-1)/size);
	}
	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ProjectDetailResponse detail(long id, boolean signedIn) {
		if (id<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "프로젝트 ID를 확인해 주세요.");
		Project project = projects.findLiveForUpdate(id).orElseThrow(this::notFound);
		// 비공개 공고와 관리자가 숨긴 공고는 현재 팀원만 본다.
		if (!project.isPubliclyVisible() && (!signedIn || !members.existsByProjectIdAndUserIdAndStatus(id, currentUser.getCurrentUserId(), ProjectMember.Status.ACTIVE))) throw notFound();
		project.recordView();
		return response(project, signedIn);
	}
	@Override public ProjectDetailResponse manage(long id) {
		Project project = existing(id);
		long userId = currentUser.getCurrentUserId();
		if (project.getHostMemberId() != userId || !members.existsByProjectIdAndUserIdAndStatus(id, userId, ProjectMember.Status.ACTIVE))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "주최자만 관리 정보를 조회할 수 있습니다.");
		return response(project, true);
	}
	private Project existing(long id) {
		if (id<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "프로젝트 ID를 확인해 주세요.");
		return projects.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}
	private ProjectDetailResponse response(Project project, boolean signedIn) {
		boolean closed = project.getStatus()!=ProjectStatus.RECRUITING || project.getRecruitmentEnd().isBefore(LocalDate.now(clock));
		long count=members.countByProjectIdAndStatus(project.getId(),ProjectMember.Status.ACTIVE);
		ProjectDetailResponse.Membership membership=null;
		if(signedIn){
			var actor=members.findByProjectIdAndUserIdAndStatus(project.getId(),currentUser.getCurrentUserId(),ProjectMember.Status.ACTIVE);
			if(actor.isPresent())membership=new ProjectDetailResponse.Membership(actor.get().getRole().name(),actor.get().getEndRequestedAt()!=null,
				members.countByProjectIdAndStatusAndEndRequestedAtIsNotNull(project.getId(),ProjectMember.Status.ACTIVE),count/2+1);
		}
		return new ProjectDetailResponse(ProjectResponse.from(project),count,closed,membership,queries.host(project.getHostMemberId()),queries.team(project.getId(),membership!=null));
	}
	private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."); }
}
