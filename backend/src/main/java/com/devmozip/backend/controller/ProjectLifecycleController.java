package com.devmozip.backend.controller;

import com.devmozip.backend.dto.project.ProjectLifecycleRequest;
import com.devmozip.backend.dto.project.ProjectLifecycleResponse;
import com.devmozip.backend.service.ProjectLifecycleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectLifecycleController {
	private final ProjectLifecycleService service;
	public ProjectLifecycleController(ProjectLifecycleService service) { this.service = service; }
	@PatchMapping("/{id}/update")
	public ProjectLifecycleResponse update(@PathVariable long id, @Valid @RequestBody ProjectLifecycleRequest request) {
		return service.update(id, request);
	}
}
