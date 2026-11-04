package com.devmozip.backend.security.handler;

import tools.jackson.databind.ObjectMapper;
import com.devmozip.backend.dto.common.ErrorResponse;
import com.devmozip.backend.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * "인증되지 않은" 요청이 보호된 경로에 왔을 때 호출된다 → 401.
 *
 * 스프링 시큐리티는 엔트리포인트를 지정하지 않으면 403 Forbidden 을 돌려준다(Http403ForbiddenEntryPoint).
 * 그러면 "토큰이 없다"(401)와 "권한이 없다"(403)를 구분할 수 없어 프론트엔드가 올바르게 대응하지 못한다.
 *
 * 토큰이 없는 경우와 있지만 쓸 수 없는 경우(만료·위조)를 메시지로 구분한다.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 토큰은 유효하지만 정지된 회원 → 로그인 API와 같은 403으로 응답한다. (JwtAuthenticationFilter가 표시)
        if (Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.SUSPENDED_ATTRIBUTE))) {
            write(response, request, HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다. 관리자에게 문의해 주세요.");
            return;
        }

        String message = request.getHeader("Authorization") == null
                ? "로그인이 필요합니다."
                : "토큰이 유효하지 않거나 만료되었습니다.";

        write(response, request, HttpStatus.UNAUTHORIZED, message);
    }

    private void write(HttpServletResponse response, HttpServletRequest request,
                       HttpStatus status, String message) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.name(),
                message,
                request.getRequestURI()
        );

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}