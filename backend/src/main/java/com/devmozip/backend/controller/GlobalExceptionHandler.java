package com.devmozip.backend.controller;

import com.devmozip.backend.dto.common.ErrorResponse;
import com.devmozip.backend.security.jwt.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 컨트롤러 안에서 던져진 예외를 ErrorResponse 로 바꾼다.
 *
 * 필터 단계의 401/403 은 시큐리티 핸들러(JsonAuthenticationEntryPoint, JsonAccessDeniedHandler)가 맡고,
 * 컨트롤러·서비스 단계의 예외는 여기서 맡는다. 두 곳 모두 같은 ErrorResponse 형식을 쓴다.
 *
 * 여기서 처리하지 않은 예외는 스프링 부트 기본 오류 응답(/error)으로 넘어가 형식이 달라지므로,
 * 마지막에 Exception 안전망을 두어 모든 예외가 ErrorResponse 로 나가게 한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 로그인: 아이디·비밀번호 불일치. AuthenticationManager 가 던진다 */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.name(),
                "아이디 또는 비밀번호가 올바르지 않습니다.",
                request.getRequestURI()
        );
    }

    /** 재발급: 리프레시 토큰이 위조·만료·폐기됨. AuthService.refresh() 가 던진다 */
    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidToken(InvalidTokenException e, HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.name(),
                e.getMessage(),
                request.getRequestURI()
        );
    }

    /** 로그인: 정지된 계정(SUSPENDED). 비밀번호는 맞았지만 이용이 막힌 상태라 403 */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException e, HttpServletRequest request) {
        return toResponse(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다. 관리자에게 문의해 주세요.", request);
    }

    /**
     * 그 밖의 인증 실패. 예: 재발급 중 회원이 삭제돼 UsernameNotFoundException 발생.
     * 아래 Exception 안전망에 잡혀 500 이 되지 않도록 401 로 따로 처리한다.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        return toResponse(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.", request);
    }

    /** 컨트롤러·서비스 단계의 권한 부족(예: 나중에 추가할 @PreAuthorize). 500 이 되지 않도록 403 으로 처리한다 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return toResponse(HttpStatus.FORBIDDEN, "이 기능을 사용할 권한이 없습니다.", request);
    }

    /** 서비스가 상태 코드와 메시지를 지정해 던진 예외 (ResponseStatusException) */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        String message = e.getReason() != null ? e.getReason() : defaultMessage(e.getStatusCode());
        return toResponse(e.getStatusCode(), message, request);
    }

    /** 요청 본문 JSON 이 깨졌거나 타입이 맞지 않음. 파서의 내부 메시지는 내보내지 않는다 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return toResponse(HttpStatus.BAD_REQUEST, "요청 본문의 형식이 올바르지 않습니다.", request);
    }

    /**
     * 안전망: 위에서 처리하지 않은 모든 예외.
     * - 스프링 MVC 예외(없는 URL 404, 지원하지 않는 메서드 405 등)는 예외가 가진 상태 코드를 그대로 쓴다.
     * - 그 외 예상하지 못한 예외는 500 으로 응답하고, 상세 내용은 서버 로그에만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            return toResponse(status, defaultMessage(status), request);
        }

        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), e);
        return toResponse(HttpStatus.INTERNAL_SERVER_ERROR, defaultMessage(HttpStatus.INTERNAL_SERVER_ERROR), request);
    }

    private ResponseEntity<ErrorResponse> toResponse(HttpStatusCode status, String message, HttpServletRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String error = httpStatus != null ? httpStatus.name() : String.valueOf(status.value());

        ErrorResponse body = ErrorResponse.of(status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** 상태 코드별 기본 안내 문구. 프론트엔드가 그대로 화면에 보여줄 수 있는 문장으로 둔다 */
    private String defaultMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "잘못된 요청입니다.";
            case 404 -> "요청한 대상을 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 요청 방식입니다.";
            case 415 -> "지원하지 않는 요청 형식입니다.";
            case 500 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            default -> status.is5xxServerError() ? "서버 오류가 발생했습니다." : "요청을 처리할 수 없습니다.";
        };
    }
}
