package com.devmozip.backend.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=PortfolioQueryController.class)
public class PortfolioQueryExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handle(ResponseStatusException error){return ProblemDetail.forStatusAndDetail(error.getStatusCode(),error.getReason());}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail badParameter(MethodArgumentTypeMismatchException error){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"조회 조건을 확인해 주세요.");}
}
