package com.devmozip.backend.controller;

import java.net.URI;
import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.ProjectDetailResponse;
import com.devmozip.backend.dto.project.ProjectOptionsResponse;
import com.devmozip.backend.dto.project.ProjectPageResponse;
import com.devmozip.backend.dto.project.ProjectResponse;
import com.devmozip.backend.dto.project.ProjectSearchRequest;
import com.devmozip.backend.service.ProjectQueryService;
import com.devmozip.backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
	private final ProjectService service;
	private final ProjectQueryService queries;

	public ProjectController(ProjectService service, ProjectQueryService queries) {
		this.service = service;
		this.queries = queries;
	}

	@PostMapping
	public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
		ProjectResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/projects/" + response.id())).body(response);
	}

	@GetMapping
	public ProjectPageResponse list(@Valid @ModelAttribute ProjectSearchRequest request) {
		return queries.search(request);
	}

	@GetMapping("/{id}")
	public ProjectDetailResponse detail(@PathVariable long id, Authentication authentication) {
		return queries.detail(id, authentication != null);
	}

	@GetMapping("/{id}/manage")
	public ProjectDetailResponse manage(@PathVariable long id) { return queries.manage(id); }

	@GetMapping("/options")
	public ProjectOptionsResponse options() { return ProjectOptionsResponse.defaults(); }
}
