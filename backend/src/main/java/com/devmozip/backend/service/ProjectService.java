package com.devmozip.backend.service;

import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.ProjectResponse;

public interface ProjectService {
	ProjectResponse create(CreateProjectRequest request);
}
