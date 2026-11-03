package com.devmozip.backend.auth;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService service;
	public AuthController(AuthService service) { this.service = service; }
	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) { return service.login(request); }
	@PostMapping("/refresh")
	public LoginResponse refresh(@RequestBody RefreshRequest request) { return service.refresh(request.refreshToken()); }
	@PostMapping("/logout")
	@ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
	public void logout(@RequestBody RefreshRequest request) { service.logout(request.refreshToken()); }
	// BFF가 서명 검증을 백엔드에 맡기고 서버 세션을 확인하는 인증 전용 조회다.
	@GetMapping("/me")
	public Map<String, String> me(Authentication authentication) {
		boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
		return Map.of("username", authentication.getName(), "role", admin ? "ADMIN" : "USER");
	}
}
