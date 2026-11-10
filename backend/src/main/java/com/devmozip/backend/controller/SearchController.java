package com.devmozip.backend.controller;

import com.devmozip.backend.dto.search.DeveloperSearchQuery;
import com.devmozip.backend.dto.search.ProjectSearchQuery;
import com.devmozip.backend.dto.search.SearchPageResponse;
import com.devmozip.backend.dto.search.SearchType;
import com.devmozip.backend.service.DeveloperSearchService;
import com.devmozip.backend.service.ProjectSearchService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WBS 6.1~6.10 통합 검색. 공개 GET이며(SecurityConfig) 팔로잉 탭만 Service에서 로그인을 확인한다.
 * 쿼리는 DTO가 직접 읽고, 형식 오류는 ResponseStatusException(400)으로 공통 ErrorResponse 형식이 된다.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
	private final DeveloperSearchService developers;
	private final ProjectSearchService projects;

	public SearchController(DeveloperSearchService developers, ProjectSearchService projects) {
		this.developers = developers;
		this.projects = projects;
	}

	@GetMapping
	public SearchPageResponse<?> search(@RequestParam MultiValueMap<String, String> params) {
		return switch (SearchType.from(params)) {
			case DEVELOPERS -> developers.search(DeveloperSearchQuery.from(params));
			case PROJECTS -> projects.search(ProjectSearchQuery.from(params));
		};
	}
}
