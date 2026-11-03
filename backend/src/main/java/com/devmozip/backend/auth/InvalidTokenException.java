package com.devmozip.backend.auth;

public class InvalidTokenException extends RuntimeException {
	public InvalidTokenException() { super("유효하지 않은 인증 정보입니다."); }
}
