package com.devmozip.backend.controller;

import com.devmozip.backend.dto.ai.CreateRecommendationRequest;
import com.devmozip.backend.dto.ai.RecommendationResponse;
import com.devmozip.backend.entity.AiRecommendationRequest.Status;
import com.devmozip.backend.service.AiRecommendationExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/conversations/{id}/recommendations")
public class AiRecommendationController {
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<com.devmozip.backend.dto.common.ErrorResponse> invalid(IllegalArgumentException error,
		jakarta.servlet.http.HttpServletRequest request) {
		return ResponseEntity.badRequest().body(com.devmozip.backend.dto.common.ErrorResponse.of(
			400, "Bad Request", error.getMessage(), request.getRequestURI()));
	}

	private final AiRecommendationExecutionService service;
	public AiRecommendationController(AiRecommendationExecutionService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<RecommendationResponse> execute(@PathVariable long id,
		@RequestHeader(value = "Idempotency-Key", required = false) String key,
		@RequestBody(required = false) CreateRecommendationRequest request) {
		var execution = service.execute(id, request == null ? null : request.messageId(), key);
		var response = execution.response();
		int status = response.status() == Status.FAILED ? 500 : response.status() == Status.PENDING ? 202 : execution.created() ? 201 : 200;
		return ResponseEntity.status(status).body(response);
	}

	@GetMapping
	public RecommendationResponse find(@PathVariable long id, @RequestParam(required = false) Long requestId) {
		return service.find(id, requestId);
	}
}
