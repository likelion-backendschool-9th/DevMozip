package com.devmozip.backend.websocket;

import java.util.Map;
import com.devmozip.backend.config.MessengerRealtimeProperties;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.WebUtils;

/** 연결 전에 Origin과 쿠키의 액세스 토큰을 확인한다. 브라우저는 WebSocket에 Authorization 헤더를 넣을 수 없다. */
@Component
public class MessengerHandshakeInterceptor implements HandshakeInterceptor {
	static final String MEMBER_ID = "messenger.memberId";
	static final String EMAIL = "messenger.email";
	static final String TOKEN_EXPIRES_AT = "messenger.tokenExpiresAt";
	private static final String ACCESS_COOKIE = "devmozip_access";

	private final MessengerRealtimeProperties properties;
	private final JwtProvider jwtProvider;
	private final SuspendedMemberStore suspendedMembers;
	private final MemberRepository members;

	public MessengerHandshakeInterceptor(MessengerRealtimeProperties properties, JwtProvider jwtProvider,
		SuspendedMemberStore suspendedMembers, MemberRepository members) {
		this.properties = properties;
		this.jwtProvider = jwtProvider;
		this.suspendedMembers = suspendedMembers;
		this.members = members;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
		Map<String, Object> attributes) {
		// 쿠키는 다른 사이트의 연결에도 자동으로 붙으므로 Origin을 먼저 본다.
		if (!properties.allowsOrigin(request.getHeaders().getOrigin())) {
			return reject(response, HttpStatus.FORBIDDEN);
		}
		String token = accessToken(request);
		if (token == null || !jwtProvider.validateAccessToken(token)) {
			return reject(response, HttpStatus.UNAUTHORIZED);
		}
		String email = jwtProvider.getUsername(token);
		Member member = members.findByEmail(email).orElse(null);
		if (member == null) {
			return reject(response, HttpStatus.UNAUTHORIZED);
		}
		if (suspendedMembers.isSuspended(email) || member.getStatus() == MemberStatus.SUSPENDED) {
			return reject(response, HttpStatus.FORBIDDEN);
		}
		attributes.put(MEMBER_ID, member.getId());
		attributes.put(EMAIL, email);
		attributes.put(TOKEN_EXPIRES_AT, jwtProvider.getExpiration(token));
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {
	}

	private static String accessToken(ServerHttpRequest request) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return null;
		}
		Cookie cookie = WebUtils.getCookie(servletRequest.getServletRequest(), ACCESS_COOKIE);
		return cookie == null ? null : cookie.getValue();
	}

	private static boolean reject(ServerHttpResponse response, HttpStatus status) {
		response.setStatusCode(status);
		return false;
	}
}
