package com.devmozip.backend.service;

import com.devmozip.backend.dto.project.*;

public interface ProjectQueryService {
	ProjectPageResponse search(ProjectSearchRequest request);
	ProjectDetailResponse detail(long id, boolean signedIn);
	ProjectPageResponse mine(ProjectSearchRequest request, MyProjectsTab tab);
	ProjectDetailResponse manage(long id);
}
