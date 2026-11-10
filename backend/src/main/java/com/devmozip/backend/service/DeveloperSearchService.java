package com.devmozip.backend.service;

import com.devmozip.backend.dto.search.DeveloperSearchItemResponse;
import com.devmozip.backend.dto.search.DeveloperSearchQuery;
import com.devmozip.backend.dto.search.SearchPageResponse;

/** 개발자 검색(WBS 6.2·6.4·6.5). 비로그인도 검색할 수 있고, 팔로잉 탭만 로그인이 필요하다. */
public interface DeveloperSearchService {
	SearchPageResponse<DeveloperSearchItemResponse> search(DeveloperSearchQuery query);
}
