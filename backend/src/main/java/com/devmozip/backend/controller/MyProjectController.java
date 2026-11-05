package com.devmozip.backend.controller;

import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/projects")
public class MyProjectController {
	private final ProjectQueryService service;
	public MyProjectController(ProjectQueryService service) { this.service=service; }
	@GetMapping
	public ProjectPageResponse list(@Valid @ModelAttribute ProjectSearchRequest request,
		@RequestParam(defaultValue = "ALL") MyProjectsTab tab) { return service.mine(request, tab); }
}
