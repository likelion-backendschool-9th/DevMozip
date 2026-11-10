package com.devmozip.backend.controller;

import com.devmozip.backend.dto.ai.ConversationDetailResponse;
import com.devmozip.backend.dto.ai.ConversationResponse;
import com.devmozip.backend.dto.ai.CreateConversationRequest;
import com.devmozip.backend.dto.ai.CreateMessageRequest;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import com.devmozip.backend.dto.ai.ConversationConditionsResponse;
import com.devmozip.backend.service.AiConversationService;
import com.devmozip.backend.service.AiMessageAnalysisService;
import com.devmozip.backend.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiConversationController {
	private final AiConversationService service;
	private final AiMessageAnalysisService analysis;

	public AiConversationController(AiConversationService service, AiMessageAnalysisService analysis) {
		this.service = service;
		this.analysis = analysis;
	}

	@PostMapping
	public ResponseEntity<ConversationResponse> create(
			@RequestBody(required = false) CreateConversationRequest request) {

		ConversationResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/ai/conversations/" + response.id())).body(response);
	}

	@GetMapping
	public List<ConversationResponse> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	public ConversationDetailResponse detail(@PathVariable long id) {
		return service.detail(id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/messages")
	public ResponseEntity<?> message(@PathVariable long id, @RequestBody CreateMessageRequest request) {
		if (Boolean.TRUE.equals(request.analyze())) {
			var response = analysis.analyze(id, request);
			return ResponseEntity.status(response.replayed() ? 200 : 201).body(response);
		}
		return ResponseEntity.status(201).body(service.addMessage(id, request));
	}

	@PutMapping("/{id}/conditions")
	public ConversationConditionsResponse conditions(@PathVariable long id, @RequestBody UpdateConditionsRequest request) {
		return service.replaceConditions(id, request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException exception, HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of(400, "Bad Request", exception.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> invalidId(HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of(400, "Bad Request", "대화 ID를 확인해 주세요.", request.getRequestURI()));
	}

	@ExceptionHandler(NoSuchElementException.class)
	ResponseEntity<ErrorResponse> notFound(HttpServletRequest request) {
		return ResponseEntity.status(404)
				.body(ErrorResponse.of(404, "Not Found", "대화를 찾을 수 없습니다.", request.getRequestURI()));
	}
}
