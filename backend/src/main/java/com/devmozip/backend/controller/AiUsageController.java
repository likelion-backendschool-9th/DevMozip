package com.devmozip.backend.controller;

import com.devmozip.backend.dto.ai.AiUsageResponse;
import com.devmozip.backend.service.AiUsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/usage")
public class AiUsageController {
	private final AiUsageService service;
	public AiUsageController(AiUsageService service) { this.service = service; }
	@GetMapping
	public ResponseEntity<AiUsageResponse> current() {
		return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.current());
	}
}
