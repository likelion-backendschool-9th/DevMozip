package com.devmozip.backend.controller;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.ProjectReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
public class ProjectReviewController {
 private final ProjectReviewService service;public ProjectReviewController(ProjectReviewService service){this.service=service;}
 @GetMapping("/api/v1/projects/{projectId}/reviews/me") public ProjectReviewView mine(@PathVariable long projectId){return service.mine(projectId);}
 @PostMapping("/api/v1/projects/{projectId}/reviews/me/link") public ProjectReviewView.Link link(@PathVariable long projectId){return service.link(projectId);}
 @PutMapping("/api/v1/projects/{projectId}/reviews/me") public ProjectReviewView submit(@PathVariable long projectId,@Valid @RequestBody SubmitProjectReviewsRequest request){return service.submit(projectId,request);}
 @GetMapping("/api/v1/projects/{projectId}/reviews/status") public java.util.List<ProjectReviewView.RequestStatus> status(@PathVariable long projectId){return service.status(projectId);}
 @PostMapping("/api/v1/projects/{projectId}/reviews/reminders") public java.util.Map<String,Integer> remind(@PathVariable long projectId){return java.util.Map.of("requested",service.remind(projectId));}
}
