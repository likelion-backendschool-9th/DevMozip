package com.devmozip.backend.controller;

import com.devmozip.backend.dto.common.ErrorResponse;
import com.devmozip.backend.dto.message.ChatDetailResponse;
import com.devmozip.backend.dto.message.ChatMessageResponse;
import com.devmozip.backend.dto.message.ReadMessageRequest;
import com.devmozip.backend.dto.message.SendMessageRequest;
import com.devmozip.backend.dto.message.StartChatRequest;
import com.devmozip.backend.dto.message.StartChatResponse;
import com.devmozip.backend.dto.message.UnreadCountResponse;
import com.devmozip.backend.service.MessageService;
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

@RestController
@RequestMapping("/api/v1/messages/chats")
public class MessageController {
	private static final String PROJECT_TYPE = "project";

	private final MessageService messageService;
	private final ProjectChatService projectChatService;

	public MessageController(MessageService messageService, ProjectChatService projectChatService) {
		this.messageService = messageService;
		this.projectChatService = projectChatService;
	}

	@PostMapping
	public StartChatResponse start(Authentication authentication, @RequestBody StartChatRequest request) {
		return messageService.startDirectChat(memberId(authentication), request.targetSlug());
	}

	/** 사이드바 탭마다 type을 나눠 부른다(D11). 두 탭의 항목 형식과 커서가 달라 한 응답에 섞지 않는다. */
	@GetMapping
	public Object list(Authentication authentication,
		@RequestParam(defaultValue = "direct") String type,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) Long cursor,
		@RequestParam(required = false) Integer size) {
		if (PROJECT_TYPE.equals(type)) {
			return projectChatService.listRooms(memberId(authentication), q, cursor, size);
		}
		return messageService.listChats(memberId(authentication), type, q, cursor, size);
	}

	@GetMapping("/unread-count")
	public UnreadCountResponse unreadCount(Authentication authentication) {
		return messageService.countUnread(memberId(authentication));
	}

	@GetMapping("/{id:[0-9]+}")
	public ChatDetailResponse detail(Authentication authentication, @PathVariable long id,
		@RequestParam(required = false) Long before,
		@RequestParam(required = false) Long after,
		@RequestParam(required = false) Integer size) {
		return messageService.getDirectChat(memberId(authentication), id, before, after, size);
	}

	@PostMapping("/{id:[0-9]+}")
	@ResponseStatus(HttpStatus.CREATED)
	public ChatMessageResponse send(Authentication authentication, @PathVariable long id,
		@RequestBody SendMessageRequest request) {
		return messageService.sendDirectMessage(memberId(authentication), id, request.body());
	}

	@PatchMapping("/{id:[0-9]+}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void read(Authentication authentication, @PathVariable long id, @RequestBody ReadMessageRequest request) {
		messageService.markDirectChatRead(memberId(authentication), id, request.lastReadMessageId());
	}

	// 공통 처리기는 이 예외를 500으로 응답하므로 숫자 파라미터 오류를 여기서 400으로 바꾼다.
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> invalidParameter(HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(
			HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.name(), "요청 값을 확인해 주세요.", request.getRequestURI()));
	}

	private long memberId(Authentication authentication) {
		return messageService.currentMemberId(authentication.getName());
	}
}
