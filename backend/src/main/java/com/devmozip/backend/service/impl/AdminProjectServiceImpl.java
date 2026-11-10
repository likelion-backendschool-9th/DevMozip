package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.admin.AdminActionRequest;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminProjectResponse;
import com.devmozip.backend.dto.admin.AdminRestrictRequest;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.repository.AdminProjectQueryRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminPaging;
import com.devmozip.backend.service.AdminProjectService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 공고 관리 (docs/admin-plan.md 5.5). 공고 행을 잠그고 바꾼다.
 * 삭제 규칙은 주최자 삭제(ProjectServiceImpl.delete, 7.5)와 같다: 모집 중인 공고만 논리 삭제하고 지원·팀원 이력은 남긴다.
 * 공개 조회 반영은 Project.isPubliclyVisible()과 각 목록의 is_restricted 조건이 맡는다.
 */
@Service
public class AdminProjectServiceImpl implements AdminProjectService {
	private final AdminAccess access;
	private final AdminProjectQueryRepository query;
	private final ProjectRepository projects;
	private final MemberRepository members;
	private final Clock clock;

	public AdminProjectServiceImpl(AdminAccess access, AdminProjectQueryRepository query, ProjectRepository projects,
		MemberRepository members, @Qualifier("projectClock") Clock clock) {
		this.access = access;
		this.query = query;
		this.projects = projects;
		this.members = members;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminPageResponse<AdminProjectResponse> list(String q, String status, String restricted, int page, int size) {
		access.require();
		ProjectStatus statusFilter = AdminPaging.option(ProjectStatus.class, status, "공고 상태를 확인해 주세요.");
		Boolean restrictedFilter = parseBoolean(restricted);
		Page<Project> rows = query.search(AdminPaging.keyword(q), statusFilter, restrictedFilter, AdminPaging.request(page, size));
		Map<Long, Member> hosts = rows.isEmpty() ? Map.of()
			: members.findAllById(rows.map(Project::getHostMemberId).getContent().stream().distinct().toList()).stream()
				.collect(Collectors.toMap(Member::getId, Function.identity()));
		return AdminPageResponse.of(rows, project -> AdminProjectResponse.of(project, hosts.get(project.getHostMemberId())));
	}

	@Override
	@Transactional
	public void act(long id, AdminActionRequest request) {
		access.require();
		if (!request.isDelete()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용하지 않는 조치입니다.");
		Project project = locked(id);
		if (project.getStatus() != ProjectStatus.RECRUITING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "모집 중인 공고만 삭제할 수 있습니다. 진행 중이거나 끝난 프로젝트는 숨김을 이용해 주세요.");
		}
		project.softDelete(LocalDateTime.now(clock));
		projects.flush();
	}

	@Override
	@Transactional
	public AdminProjectResponse restrict(long id, AdminRestrictRequest request) {
		access.require();
		Project project = locked(id);
		project.restrictByAdmin(request.restricted());
		projects.flush();
		return AdminProjectResponse.of(project, members.findById(project.getHostMemberId()).orElse(null));
	}

	private Project locked(long id) {
		return projects.findLiveForUpdate(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));
	}

	private static Boolean parseBoolean(String value) {
		if (value == null || value.isBlank()) return null;
		if ("true".equals(value)) return Boolean.TRUE;
		if ("false".equals(value)) return Boolean.FALSE;
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "숨김 여부를 확인해 주세요.");
	}
}
