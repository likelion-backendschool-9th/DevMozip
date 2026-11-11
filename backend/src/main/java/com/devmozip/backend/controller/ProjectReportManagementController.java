package com.devmozip.backend.controller;
import jakarta.validation.Valid;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectReportManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
// 관리자 처리(목록·처리)는 /api/v1/admin/reports(AdminReportController)로 옮겼다 (docs/admin-plan.md D5).
@RestController
public class ProjectReportManagementController {
	private final ProjectReportManagementService service;
	public ProjectReportManagementController(ProjectReportManagementService service){this.service=service;}
	@PostMapping("/api/v1/projects/{projectId}/members/{memberId}/reports") @ResponseStatus(HttpStatus.CREATED)
	public ProjectReportResponse report(@PathVariable long projectId,@PathVariable long memberId,@Valid @RequestBody CreateProjectMemberReportRequest body){return service.reportMember(projectId,memberId,body);}
}
