package com.devmozip.backend.service;

import java.util.List;
import com.devmozip.backend.dto.project.ProjectBookmarkResponse;
import com.devmozip.backend.dto.project.ProjectListItemResponse;

public interface ProjectBookmarkService {
	ProjectBookmarkResponse save(long projectId);
	ProjectBookmarkResponse remove(long projectId);
	List<ProjectListItemResponse> list();
}
