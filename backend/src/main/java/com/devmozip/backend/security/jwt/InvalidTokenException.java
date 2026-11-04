package com.devmozip.backend.security.jwt;

/** 리프레시 토큰이 위조·만료됐거나 서버가 더 이상 인정하지 않을 때 던진다. 컨트롤러가 401로 바꾼다. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
