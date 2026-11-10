package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.search.ProjectSearchItemResponse;
import com.devmozip.backend.dto.search.ProjectSearchQuery;
import com.devmozip.backend.dto.search.SearchPageResponse;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectSearchRepository;
import com.devmozip.backend.service.ProjectRoleValues;
import com.devmozip.backend.service.ProjectSearchService;
import com.devmozip.backend.service.SearchKeywords;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7.1 목록 조회 코드(ProjectQueryService)를 쓰지 않는 탐색 전용 조회. 모집 직군 값 변환은 ProjectRoleValues를 호출만 한다.
 * 수와 목록이 한 시점의 값이 되도록 REPEATABLE_READ로 읽는다.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProjectSearchServiceImpl implements ProjectSearchService {
	private final ProjectSearchRepository searches;
	private final ProjectRepository projects;
	private final Clock clock;

	public ProjectSearchServiceImpl(ProjectSearchRepository searches, ProjectRepository projects, @Qualifier("searchClock") Clock clock) {
		this.searches = searches;
		this.projects = projects;
		this.clock = clock;
	}

	@Override
	public SearchPageResponse<ProjectSearchItemResponse> search(ProjectSearchQuery query) {
		LocalDate today = LocalDate.now(clock);
		var criteria = new ProjectSearchRepository.Criteria(
			SearchKeywords.tokens(query.keyword()),
			query.jobRole() == null ? null : ProjectRoleValues.key(query.jobRole()),
			query.status(),
			query.interests().stream().map(SearchKeywords::normalize).distinct().toList(),
			query.techStacks().stream().map(SearchKeywords::normalize).distinct().toList());
		var result = searches.search(criteria, today, query.page(), query.size());

		var byId = projects.findAllById(result.rows().stream().map(ProjectSearchRepository.Row::id).toList()).stream()
			.collect(Collectors.toMap(Project::getId, Function.identity()));
		var items = result.rows().stream()
			.map(row -> {
				Project project = byId.get(row.id());
				return project == null ? null : ProjectSearchItemResponse.from(project, row.memberCount(), today);
			})
			.filter(Objects::nonNull)
			.toList();
		return SearchPageResponse.of(items, query.page(), query.size(), result.total());
	}
}
