package com.devmozip.backend.controller;

import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/reviews")
public class ProjectReviewController {
	private final ProjectReviewService service;

	public ProjectReviewController(ProjectReviewService service) {
		this.service = service;
	}

	@GetMapping("/me")
	public ProjectReviewResponse mine(@PathVariable long projectId) {
		return service.mine(projectId);
	}

	@PostMapping("/me/link")
	public ProjectReviewLinkResponse link(@PathVariable long projectId) {
		return service.link(projectId);
	}

	@PutMapping("/me")
	public ProjectReviewResponse submit(@PathVariable long projectId,
		@Valid @RequestBody SubmitProjectReviewsRequest request) {
		return service.submit(projectId, request);
	}

	@GetMapping("/status")
	public List<ProjectReviewRequestStatusResponse> status(@PathVariable long projectId) {
		return service.status(projectId);
	}

	@PostMapping("/reminders")
	public ProjectReviewReminderResponse remind(@PathVariable long projectId) {
		return new ProjectReviewReminderResponse(service.remind(projectId));
	}
}
