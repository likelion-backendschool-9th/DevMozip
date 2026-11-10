package com.devmozip.backend.controller;

import com.devmozip.backend.dto.report.CreateUserReportRequest;
import com.devmozip.backend.service.UserReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserReportController {
	private final UserReportService userReportService;

	public UserReportController(UserReportService userReportService) {
		this.userReportService = userReportService;
	}

	@PostMapping("/api/v1/users/{slug}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public void report(Authentication authentication, @PathVariable String slug, @RequestBody CreateUserReportRequest request) {
		userReportService.report(authentication.getName(), slug, request.reason());
	}
}
