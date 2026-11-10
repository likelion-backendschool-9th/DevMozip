package com.devmozip.backend.service;

import com.devmozip.backend.dto.project.ProjectLifecycleRequest;
import com.devmozip.backend.dto.project.ProjectLifecycleResponse;

public interface ProjectLifecycleService {
	ProjectLifecycleResponse update(long id, ProjectLifecycleRequest request);
}
