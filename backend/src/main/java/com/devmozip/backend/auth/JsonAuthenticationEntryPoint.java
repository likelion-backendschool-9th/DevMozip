package com.devmozip.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private final ObjectMapper mapper;
	public JsonAuthenticationEntryPoint(ObjectMapper mapper) { this.mapper = mapper; }
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
		response.setStatus(401);
		response.setContentType("application/json;charset=UTF-8");
		mapper.writeValue(response.getOutputStream(), ErrorResponse.of(401, "Unauthorized", "인증이 필요합니다.", request.getRequestURI()));
	}
}
