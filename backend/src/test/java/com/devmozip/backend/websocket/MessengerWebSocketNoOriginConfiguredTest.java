package com.devmozip.backend.websocket;

import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DOMAINNAME이 없는 서버도 정상으로 뜨고 WebSocket만 모두 거부한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.datasource.url=jdbc:h2:mem:messenger-websocket-no-origin",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"messenger.realtime.allowed-origin="
})
@ActiveProfiles("test")
class MessengerWebSocketNoOriginConfiguredTest {
	@LocalServerPort int port;
	@Autowired JwtProvider jwt;
	@Autowired MemberRepository members;
	private String token;

	@BeforeEach void signIn() {
		token = jwt.createAccessToken(TestMembers.create(members).getEmail(), "USER");
	}

	@Test void browserOriginIsRejected() throws Exception {
		assertEquals(403, TestSocket.handshakeStatus(port, "/ws/messages", token, TestSocket.ALLOWED_ORIGIN));
	}

	@Test void sameOriginAsServerIsRejected() throws Exception {
		assertEquals(403, TestSocket.handshakeStatus(port, "/ws/messages", token, "http://localhost:" + port));
	}

	@Test void missingOriginIsRejected() throws Exception {
		assertEquals(403, TestSocket.handshakeStatus(port, "/ws/messages", token, null));
	}
}
