package com.devmozip.backend.controller;

import com.devmozip.backend.dto.common.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 피드·댓글 API의 요청 형식 오류를 500이 아니라 400으로 응답한다. 그 밖의 예외는 GlobalExceptionHandler가 같은 형식으로 처리한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {FeedController.class, CommentController.class})
public class FeedExceptionHandler {
	// page=abc, view=yes처럼 쿼리 값의 타입이 맞지 않음
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> badParameter(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
		return badRequest("조회 조건을 확인해 주세요.", request);
	}

	// 작성·수정 요청에 JSON 파트 "post"가 없음
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ErrorResponse> missingPart(MissingServletRequestPartException exception, HttpServletRequest request) {
		return badRequest("게시글 내용을 확인해 주세요.", request);
	}

	private static ResponseEntity<ErrorResponse> badRequest(String message, HttpServletRequest request) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), status.name(), message, request.getRequestURI()));
	}
}
