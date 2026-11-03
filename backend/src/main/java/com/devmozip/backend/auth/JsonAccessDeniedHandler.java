package com.devmozip.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
	private final ObjectMapper mapper;
	public JsonAccessDeniedHandler(ObjectMapper mapper) { this.mapper = mapper; }
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
		response.setStatus(403);
		response.setContentType("application/json;charset=UTF-8");
		mapper.writeValue(response.getOutputStream(), ErrorResponse.of(403, "Forbidden", "접근 권한이 없습니다.", request.getRequestURI()));
	}
}
