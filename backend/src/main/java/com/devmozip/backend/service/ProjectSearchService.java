package com.devmozip.backend.service;

import com.devmozip.backend.dto.search.ProjectSearchItemResponse;
import com.devmozip.backend.dto.search.ProjectSearchQuery;
import com.devmozip.backend.dto.search.SearchPageResponse;

/** 프로젝트 탐색(WBS 6.3). 비로그인도 검색할 수 있다. */
public interface ProjectSearchService {
	SearchPageResponse<ProjectSearchItemResponse> search(ProjectSearchQuery query);
}
