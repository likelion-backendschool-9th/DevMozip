package com.devmozip.backend.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ProfileController.class)
public class ProfileExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handle(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(), exception.getReason());
        problem.setTitle("프로필 조회 실패");
        problem.setProperty("code", "PROFILE_NOT_FOUND");
        return problem;
    }
}
