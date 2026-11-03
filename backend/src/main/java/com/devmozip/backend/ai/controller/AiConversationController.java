package com.devmozip.backend.ai.controller;

import com.devmozip.backend.ai.service.AiConversationService;
import com.devmozip.backend.auth.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.devmozip.backend.ai.dto.CreateConversationRequest;
import com.devmozip.backend.ai.dto.CreateMessageRequest;
import com.devmozip.backend.ai.dto.ConversationResponse;
import com.devmozip.backend.ai.dto.ConversationDetailResponse;
import com.devmozip.backend.ai.dto.MessageResponse;

@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiConversationController {
	private final AiConversationService service;
	public AiConversationController(AiConversationService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<ConversationResponse> create(@RequestBody(required = false) CreateConversationRequest request) {
		ConversationResponse response = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/ai/conversations/" + response.id())).body(response);
	}
	@GetMapping
	public List<ConversationResponse> list() { return service.list(); }
	@GetMapping("/{id}")
	public ConversationDetailResponse detail(@PathVariable long id) { return service.detail(id); }
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
	@PostMapping("/{id}/messages")
	public ResponseEntity<MessageResponse> message(@PathVariable long id, @RequestBody CreateMessageRequest request) {
		return ResponseEntity.status(201).body(service.addMessage(id, request));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException exception, HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad Request", exception.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> invalidId(HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad Request", "대화 ID를 확인해 주세요.", request.getRequestURI()));
	}

	@ExceptionHandler(NoSuchElementException.class)
	ResponseEntity<ErrorResponse> notFound(HttpServletRequest request) {
		return ResponseEntity.status(404).body(ErrorResponse.of(404, "Not Found", "대화를 찾을 수 없습니다.", request.getRequestURI()));
	}
}
