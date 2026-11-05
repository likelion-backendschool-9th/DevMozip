package com.devmozip.backend.service.impl;

import java.net.URI;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.ProjectResponse;
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

	public ProjectServiceImpl(ProjectRepository projects, ProjectMemberRepository members,
		ProjectCurrentUserProvider currentUser, @Qualifier("projectClock") Clock clock, Validator validator) {
		this.projects = projects;
		this.members = members;
		this.currentUser = currentUser;
		this.clock = clock;
		this.validator = validator;
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
		if (request.recruitmentStart().isAfter(request.recruitmentEnd())
			|| request.developmentStart().isAfter(request.developmentEnd())
			|| request.recruitmentEnd().isAfter(request.developmentEnd())
			|| request.recruitmentEnd().isBefore(LocalDate.now(clock))) {
			throw invalid("모집·개발 날짜 순서를 확인해 주세요. 지난 모집 마감일은 사용할 수 없습니다.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		Project project = projects.save(new Project(hostId, request.title().strip(), trim(request.summary()),
			request.description().strip(), trim(request.goal()), image, request.isPublic(), slots, stacks, interests,
			request.recruitmentStart(), request.recruitmentEnd(), request.developmentStart(), request.developmentEnd(), now));
		members.saveAndFlush(ProjectMember.host(project, hostId, now));
		return ProjectResponse.from(project);
	}

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
