package com.devmozip.backend.controller;

import java.net.URI;
import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.ProjectOptionsResponse;
import com.devmozip.backend.dto.project.ProjectResponse;
import com.devmozip.backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
	private final ProjectService service;
	public ProjectController(ProjectService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
		ProjectResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/projects/" + response.id())).body(response);
	}

	@GetMapping("/options")
	public ProjectOptionsResponse options() { return ProjectOptionsResponse.defaults(); }
}
