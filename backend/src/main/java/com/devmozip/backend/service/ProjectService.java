package com.devmozip.backend.service;

import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.ProjectResponse;
import com.devmozip.backend.dto.project.UpdateProjectRequest;

public interface ProjectService {
	ProjectResponse create(CreateProjectRequest request);
	ProjectResponse update(long id, UpdateProjectRequest request);
	void delete(long id);
}
