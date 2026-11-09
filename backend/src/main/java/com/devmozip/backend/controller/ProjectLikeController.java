package com.devmozip.backend.controller;

import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectLikeService;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectLikeController {
	private final ProjectLikeService service;
	public ProjectLikeController(ProjectLikeService service){this.service=service;}
	@PostMapping("/{id}/like") public ProjectLikeResponse save(@PathVariable long id){return service.save(id);}
	@DeleteMapping("/{id}/like") public ProjectLikeResponse remove(@PathVariable long id){return service.remove(id);}
	@GetMapping("/likes") public List<ProjectListItemResponse> list(){return service.list();}
}
