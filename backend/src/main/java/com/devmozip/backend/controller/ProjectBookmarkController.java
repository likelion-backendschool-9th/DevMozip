package com.devmozip.backend.controller;

import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectBookmarkService;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectBookmarkController {
	private final ProjectBookmarkService service;
	public ProjectBookmarkController(ProjectBookmarkService service){this.service=service;}
	@PostMapping("/{id}/bookmark") public ProjectBookmarkResponse save(@PathVariable long id){return service.save(id);}
	@DeleteMapping("/{id}/bookmark") public ProjectBookmarkResponse remove(@PathVariable long id){return service.remove(id);}
	@GetMapping("/bookmarks") public List<ProjectListItemResponse> list(){return service.list();}
}
