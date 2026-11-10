package com.devmozip.backend.controller;

import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectReportController {
	private final ProjectReportService service;
	public ProjectReportController(ProjectReportService service){this.service=service;}
	@PostMapping("/{id}/reports") @ResponseStatus(HttpStatus.CREATED)
	public ProjectReportResponse report(@PathVariable long id,@Valid @RequestBody CreateProjectReportRequest request){return service.report(id,request);}
}
