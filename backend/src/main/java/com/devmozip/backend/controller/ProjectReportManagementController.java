package com.devmozip.backend.controller;
import jakarta.validation.Valid;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.service.ProjectReportManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
public class ProjectReportManagementController {
	private final ProjectReportManagementService service;
	public ProjectReportManagementController(ProjectReportManagementService service){this.service=service;}
	@PostMapping("/api/v1/projects/{projectId}/members/{memberId}/reports") @ResponseStatus(HttpStatus.CREATED)
	public ProjectReportResponse report(@PathVariable long projectId,@PathVariable long memberId,@Valid @RequestBody CreateProjectMemberReportRequest body){return service.reportMember(projectId,memberId,body);}
	@GetMapping("/api/v1/admin/project-reports")
	public ProjectReportPageResponse list(@RequestParam(required=false) Report.Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(status,page,size);}
	@PatchMapping("/api/v1/admin/project-reports/{id}")
	public ManagedProjectReportResponse handle(@PathVariable long id,@Valid @RequestBody HandleProjectReportRequest body){return service.handle(id,body);}
}
