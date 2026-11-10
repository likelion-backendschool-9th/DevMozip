package com.devmozip.backend.controller;

import com.devmozip.backend.dto.portfolio.PortfolioDetailResponse;
import com.devmozip.backend.service.ProjectPortfolioSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/applications/{applicationId}/portfolios")
public class ProjectPortfolioSnapshotController {
	private final ProjectPortfolioSnapshotService service;
	@GetMapping("/{snapshotId}")
	public PortfolioDetailResponse detail(@PathVariable long projectId,@PathVariable long applicationId,@PathVariable long snapshotId){return service.detail(projectId,applicationId,snapshotId);}
}
