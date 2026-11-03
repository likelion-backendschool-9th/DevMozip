package com.devmozip.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
	@ExceptionHandler({AuthenticationException.class, InvalidTokenException.class, io.jsonwebtoken.JwtException.class})
	ResponseEntity<ErrorResponse> unauthorized(Exception exception, HttpServletRequest request) {
		return ResponseEntity.status(401).body(ErrorResponse.of(401, "Unauthorized", "인증 정보를 확인해 주세요.", request.getRequestURI()));
	}
	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ErrorResponse> unavailable(DataAccessException exception, HttpServletRequest request) {
		return ResponseEntity.status(503).body(ErrorResponse.of(503, "Service Unavailable", "잠시 후 다시 시도해 주세요.", request.getRequestURI()));
	}
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> invalidBody(HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad Request", "요청 형식을 확인해 주세요.", request.getRequestURI()));
	}
}
