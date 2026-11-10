package com.devmozip.backend.service;

import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.ProjectApplication.Status;

public interface ProjectApplicationService {
	ProjectApplicationResponse apply(long projectId,CreateProjectApplicationRequest request);
	List<ProjectApplicationResponse> list(long projectId,Status status);
	ProjectApplicationOptions options(long projectId);
	ProjectApplicationResponse update(long projectId,long applicationId,UpdateProjectApplicationRequest request);
	default List<ProjectMemberResponse> members(long projectId,String jobRole){return members(projectId,jobRole,false);}
	List<ProjectMemberResponse> members(long projectId,String jobRole,boolean includeInactive);
}
