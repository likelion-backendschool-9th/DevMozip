package com.devmozip.backend.service.impl;

import java.net.URI;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.ProjectResponse;
import com.devmozip.backend.dto.project.UpdateProjectRequest;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.service.ProjectApplicationUsage;
import com.devmozip.backend.service.ProjectPeriodPolicy;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectCurrentUserProvider currentUser;
	private final Clock clock;
	private final Validator validator;
	private final ProjectApplicationUsage applications;
	private final ProjectPeriodPolicy periods;

	public ProjectServiceImpl(ProjectRepository projects, ProjectMemberRepository members,
		ProjectCurrentUserProvider currentUser, @Qualifier("projectClock") Clock clock, Validator validator, ProjectApplicationUsage applications, ProjectPeriodPolicy periods) {
		this.projects = projects;
		this.members = members;
		this.currentUser = currentUser;
		this.clock = clock;
		this.validator = validator;
		this.applications = applications;
		this.periods = periods;
	}

	@Override
	@Transactional
	public ProjectResponse create(CreateProjectRequest request) {
		long hostId = currentUser.getCurrentUserId();
		if (hostId <= 0) throw invalid("주최자 ID를 확인해 주세요.");
		if (request == null || !validator.validate(request).isEmpty()) throw invalid("프로젝트 필수 입력과 길이·인원·경력을 확인해 주세요.");
		Set<Object> roles = new HashSet<>();
		var slots = request.recruitmentSlots().stream().map(slot -> {
			if (!roles.add(slot.roleCode())) throw invalid("모집 직군을 중복 선택할 수 없습니다.");
			return new ProjectRecruitmentSlot(slot.roleCode(), slot.capacity(), slot.minimumYears());
		}).toList();
		List<String> stacks = normalize(request.techStacks());
		List<String> interests = normalize(request.interests());
		String image = request.imageUrl().strip();
		validateImage(image);
		periods.validate(request);
		if (request.recruitmentEnd().isBefore(LocalDate.now(clock))) throw invalid("지난 모집 마감일은 사용할 수 없습니다.");

		LocalDateTime now = LocalDateTime.now(clock);
		Project project = projects.save(new Project(hostId, request.title().strip(), trim(request.summary()),
			request.description().strip(), trim(request.goal()), image, request.isPublic(), slots, stacks, interests,
			request.recruitmentStart(), request.recruitmentEnd(), request.developmentStart(), request.developmentEnd(), now));
		members.saveAndFlush(ProjectMember.host(project, hostId, now));
		return ProjectResponse.from(project);
	}

	@Override
	@Transactional
	public ProjectResponse update(long id, UpdateProjectRequest request) {
		Project project = ownedForUpdate(id);
		if (project.getStatus() == ProjectStatus.COMPLETED) throw conflict("종료된 프로젝트는 수정할 수 없습니다.");
		if (request == null || !validator.validate(request).isEmpty()) throw invalid("프로젝트 필수 입력과 길이·인원·경력을 확인해 주세요.");
		var input = request.asCreateRequest();
		periods.validateUpdate(project, input);
		Set<ProjectJobRole> codes = new HashSet<>();
		var slots = input.recruitmentSlots().stream().map(slot -> {
			if (!codes.add(slot.roleCode())) throw invalid("모집 직군을 중복 선택할 수 없습니다.");
			return new ProjectRecruitmentSlot(slot.roleCode(), slot.capacity(), slot.minimumYears());
		}).toList();
		List<String> stacks = normalize(input.techStacks());
		List<String> interests = normalize(input.interests());
		String image = input.imageUrl().strip();
		validateImage(image);
		validateSlots(project, slots);
		project.update(input.title().strip(), trim(input.summary()), input.description().strip(), trim(input.goal()),
			image, input.isPublic(), slots, stacks, interests, input.recruitmentStart(), input.recruitmentEnd(),
			input.developmentStart(), input.developmentEnd(), LocalDateTime.now(clock));
		projects.flush();
		return ProjectResponse.from(project);
	}

	@Override
	@Transactional
	public void delete(long id) {
		Project project = ownedForUpdate(id);
		if (project.getStatus() != ProjectStatus.RECRUITING) throw conflict("모집 중인 프로젝트만 삭제할 수 있습니다.");
		// 지원·팀원 이력은 유지한다. 모집 중이면 인원 존재 자체로 삭제를 막지 않는다.
		project.softDelete(LocalDateTime.now(clock));
		projects.flush();
	}

	private Project ownedForUpdate(long id) {
		long userId = currentUser.getCurrentUserId();
		if (id <= 0) throw invalid("프로젝트 ID를 확인해 주세요.");
		Project project = projects.findLiveForUpdate(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
		if (project.getHostMemberId() != userId || !members.existsByProjectIdAndUserIdAndStatus(id, userId, ProjectMember.Status.ACTIVE)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "주최자만 공고를 변경할 수 있습니다.");
		return project;
	}

	private void validateSlots(Project project, List<ProjectRecruitmentSlot> slots) {
		Map<ProjectJobRole, Integer> capacities = new EnumMap<>(ProjectJobRole.class);
		for (var slot : slots) capacities.put(slot.roleCode(), slot.capacity());
		Set<ProjectJobRole> used = new HashSet<>(applications.usedRoles(project.getId()));
		Map<ProjectJobRole, Integer> active = new EnumMap<>(ProjectJobRole.class);
		for (ProjectMember member : members.findByProjectId(project.getId())) {
			if (member.getRole() == ProjectMember.Role.HOST || member.getJobRole() == null || member.getStatus() != ProjectMember.Status.ACTIVE) continue;
			used.add(member.getJobRole());
			if (member.getStatus() == ProjectMember.Status.ACTIVE) active.merge(member.getJobRole(), 1, Integer::sum);
		}
		if (!capacities.keySet().containsAll(used)) throw conflict("지원자·참여자가 있는 모집 직군은 삭제하거나 변경할 수 없습니다.");
		for (var entry : active.entrySet()) if (capacities.getOrDefault(entry.getKey(), 0) < entry.getValue())
			throw conflict("모집 정원을 해당 직군의 현재 참여 인원보다 줄일 수 없습니다.");
	}

	private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

	private List<String> normalize(List<String> values) {
		Set<String> seen = new HashSet<>();
		return values.stream().map(value -> {
			String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
			if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 30)
				throw invalid("기술 스택·관심 분야는 1~30자로 입력해 주세요.");
			if (!seen.add(normalized.toLowerCase(Locale.ROOT))) throw invalid("기술 스택·관심 분야를 중복 입력할 수 없습니다.");
			return normalized;
		}).toList();
	}
	private void validateImage(String value) {
		try {
			URI uri = URI.create(value);
			if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
				|| uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
		} catch (IllegalArgumentException exception) {
			throw invalid("이미지는 http 또는 https URL로 입력해 주세요.");
		}
	}
	private String trim(String value) { return value == null ? null : value.strip(); }
	private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
