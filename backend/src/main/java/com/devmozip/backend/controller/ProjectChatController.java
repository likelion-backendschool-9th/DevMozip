package com.devmozip.backend.controller;

import com.devmozip.backend.dto.common.ErrorResponse;
import com.devmozip.backend.dto.message.ProjectChatDetailResponse;
import com.devmozip.backend.dto.message.ProjectChatMessageResponse;
import com.devmozip.backend.dto.message.ReadMessageRequest;
import com.devmozip.backend.dto.message.SendMessageRequest;
import com.devmozip.backend.dto.message.UnreadCountResponse;
import com.devmozip.backend.service.ProjectChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 프로젝트 단톡방(7.19~7.21). {id}는 프로젝트 ID다. 목록은 GET /messages/chats?type=project다. */
@RestController
@RequestMapping("/api/v1/messages/projects")
public class ProjectChatController {
	private final ProjectChatService projectChatService;

	public ProjectChatController(ProjectChatService projectChatService) {
		this.projectChatService = projectChatService;
	}

	@GetMapping("/unread-count")
	public UnreadCountResponse unreadCount(Authentication authentication) {
		return projectChatService.countUnread(memberId(authentication));
	}

	@GetMapping("/{id:[0-9]+}")
	public ProjectChatDetailResponse detail(Authentication authentication, @PathVariable long id,
		@RequestParam(required = false) Long before,
		@RequestParam(required = false) Long after,
		@RequestParam(required = false) Integer size) {
		return projectChatService.getProjectChat(memberId(authentication), id, before, after, size);
	}

	@PostMapping("/{id:[0-9]+}")
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectChatMessageResponse send(Authentication authentication, @PathVariable long id,
		@RequestBody SendMessageRequest request) {
		return projectChatService.sendProjectMessage(memberId(authentication), id, request.body());
	}

	// 논리 삭제라 DELETE 메서드를 쓰지 않는다(D10).
	@PostMapping("/{id:[0-9]+}/delete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(Authentication authentication, @PathVariable long id) {
		projectChatService.deleteProjectChat(memberId(authentication), id);
	}

	@PatchMapping("/{id:[0-9]+}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void read(Authentication authentication, @PathVariable long id, @RequestBody ReadMessageRequest request) {
		projectChatService.markProjectChatRead(memberId(authentication), id, request.lastReadMessageId());
	}

	// 공통 처리기는 이 예외를 500으로 응답하므로 숫자 파라미터 오류를 여기서 400으로 바꾼다.
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> invalidParameter(HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(
			HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.name(), "요청 값을 확인해 주세요.", request.getRequestURI()));
	}

	private long memberId(Authentication authentication) {
		return projectChatService.currentMemberId(authentication.getName());
	}
}
