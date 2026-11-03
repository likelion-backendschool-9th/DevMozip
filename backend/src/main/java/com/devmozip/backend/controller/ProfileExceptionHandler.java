package com.devmozip.backend.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ProfileController.class, ProfileUpdateController.class})
public class ProfileExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handle(ResponseStatusException exception) {
        String code = switch (exception.getStatusCode().value()) {
            case 404 -> "PROFILE_NOT_FOUND";
            case 409 -> "PROFILE_CONFLICT";
            case 401 -> "AUTHENTICATION_REQUIRED";
            case 403 -> "PROFILE_FORBIDDEN";
            default -> "PROFILE_INVALID_REQUEST";
        };
        return problem(exception.getStatusCode().value(), exception.getReason(), code);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ProblemDetail invalidInput(Exception exception) {
        return problem(400, "프로필 입력 값을 확인해 주세요.", "PROFILE_INVALID_REQUEST");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(DataIntegrityViolationException exception) {
        return problem(409, "다른 회원과 중복되는 정보이거나 저장 조건을 만족하지 못했습니다.", "PROFILE_CONFLICT");
    }

    private ProblemDetail problem(int status, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setTitle("프로필 요청 실패");
        problem.setProperty("code", code);
        return problem;
    }
}
