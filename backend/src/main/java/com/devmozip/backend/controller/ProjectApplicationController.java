package com.devmozip.backend.controller;
import java.net.URI;
import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.ProjectApplication.Status;
import com.devmozip.backend.service.ProjectApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/projects/{projectId}")
public class ProjectApplicationController {
	private final ProjectApplicationService service;
	public ProjectApplicationController(ProjectApplicationService service){this.service=service;}
	@PostMapping("/applications") public ResponseEntity<ProjectApplicationResponse> apply(@PathVariable long projectId,@Valid @RequestBody CreateProjectApplicationRequest request){var response=service.apply(projectId,request);return ResponseEntity.created(URI.create("/api/v1/projects/"+projectId+"/applications/"+response.id())).body(response);}
	@GetMapping("/applications") public List<ProjectApplicationResponse> list(@PathVariable long projectId,@RequestParam(required=false) Status status){return service.list(projectId,status);}
	@PatchMapping("/applications/{applicationId}") public ProjectApplicationResponse update(@PathVariable long projectId,@PathVariable long applicationId,@Valid @RequestBody UpdateProjectApplicationRequest request){return service.update(projectId,applicationId,request);}
	@GetMapping("/application-options") public ProjectApplicationOptions options(@PathVariable long projectId){return service.options(projectId);}
	@GetMapping("/members") public List<ProjectMemberResponse> members(@PathVariable long projectId,@RequestParam(required=false) String jobRole){return service.members(projectId,jobRole);}
}
