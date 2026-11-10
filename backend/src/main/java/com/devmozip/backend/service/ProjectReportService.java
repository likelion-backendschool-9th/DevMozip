package com.devmozip.backend.service;

import com.devmozip.backend.dto.project.*;
public interface ProjectReportService {
	ProjectReportResponse report(long projectId, CreateProjectReportRequest request);
}
