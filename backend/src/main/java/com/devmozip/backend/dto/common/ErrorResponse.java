package com.devmozip.backend.dto.common;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 모든 에러 응답의 공통 형식.
 *
 * 스프링 시큐리티의 401/403, 컨트롤러의 예외, 나중에 추가될 검증 오류까지
 * 전부 이 모양으로 내려보내면 프론트엔드는 "status 와 message 를 읽는다" 한 가지만 알면 된다.
 */
@Data
@Builder
public class ErrorResponse {
    private int status;        // HTTP 상태 코드 (401, 403, ...)
    private String error;      // 상태 코드의 이름 (UNAUTHORIZED, FORBIDDEN, ...)
    private String message;    // 사람이 읽을 설명. 프론트가 그대로 화면에 보여줄 수 있다
    private String path;       // 요청 경로. 디버깅용
    private Instant timestamp;

    public static ErrorResponse of(int status, String error, String message, String path) {
        return ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();
    }
}