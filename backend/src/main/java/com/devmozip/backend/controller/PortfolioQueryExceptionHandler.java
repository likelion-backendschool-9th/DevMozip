package com.devmozip.backend.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={PortfolioQueryController.class,PortfolioMutationController.class})
public class PortfolioQueryExceptionHandler {
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ProblemDetail storage(Exception error){return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오 저장소를 사용할 수 없습니다.");}
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handle(ResponseStatusException error){return ProblemDetail.forStatusAndDetail(error.getStatusCode(),error.getReason());}
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ProblemDetail invalidBody(Exception error){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"포트폴리오 입력 형식을 확인해 주세요.");}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail badParameter(MethodArgumentTypeMismatchException error){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"조회 조건을 확인해 주세요.");}
}
