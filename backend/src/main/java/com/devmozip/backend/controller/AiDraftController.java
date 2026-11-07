package com.devmozip.backend.controller;

import com.devmozip.backend.dto.ai.ConversationDetailResponse;
import com.devmozip.backend.dto.ai.SyncDraftRequest;
import com.devmozip.backend.dto.ai.RecommendationRequestSummary;
import com.devmozip.backend.dto.common.ErrorResponse;
import com.devmozip.backend.service.AiDraftService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiDraftController {
	private final AiDraftService service;
	public AiDraftController(AiDraftService service) { this.service = service; }
	@PostMapping("/sync")
	public ConversationDetailResponse sync(@RequestBody SyncDraftRequest request) { return service.sync(request); }
	@GetMapping("/{id}/recommendations/history")
	public List<RecommendationRequestSummary> history(@PathVariable long id) { return service.history(id); }
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> invalid(IllegalArgumentException error, HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad Request", error.getMessage(), request.getRequestURI()));
	}
}
