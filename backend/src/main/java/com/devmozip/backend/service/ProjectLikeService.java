package com.devmozip.backend.service;

import java.util.List;
import com.devmozip.backend.dto.project.ProjectLikeResponse;
import com.devmozip.backend.dto.project.ProjectListItemResponse;

public interface ProjectLikeService {
	ProjectLikeResponse save(long projectId);
	ProjectLikeResponse remove(long projectId);
	List<ProjectListItemResponse> list();
}
