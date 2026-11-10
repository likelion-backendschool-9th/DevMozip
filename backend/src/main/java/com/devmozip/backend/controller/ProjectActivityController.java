package com.devmozip.backend.controller;

import com.devmozip.backend.dto.project.ProjectActivityPageResponse;
import com.devmozip.backend.service.ProjectActivityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{slug}/projects")
public class ProjectActivityController {
	private final ProjectActivityService service;
	public ProjectActivityController(ProjectActivityService service) { this.service = service; }
	@GetMapping
	public ProjectActivityPageResponse list(@PathVariable String slug, Authentication authentication,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return service.find(slug, authentication != null, page, size);
	}
}
