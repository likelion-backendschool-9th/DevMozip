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

/**
 * page=abc처럼 쿼리 값의 타입이 맞지 않으면 500이 아니라 400으로 응답한다.
 * 그 밖의 예외는 GlobalExceptionHandler가 같은 ErrorResponse 형식으로 처리한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FollowController.class)
public class FollowExceptionHandler {
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> badParameter(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status)
			.body(ErrorResponse.of(status.value(), status.name(), "조회 조건을 확인해 주세요.", request.getRequestURI()));
	}
}
