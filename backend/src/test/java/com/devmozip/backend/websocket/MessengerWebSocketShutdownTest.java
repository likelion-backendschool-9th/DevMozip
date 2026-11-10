package com.devmozip.backend.websocket;

import com.devmozip.backend.BackendApplication;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 서버를 실제로 닫는다. 테스트 프레임워크가 관리하는 컨텍스트는 테스트 안에서 닫을 수 없어 직접 띄운다.
 * Tomcat도 종료 때 1001을 보내므로 이유 문구로 저장소가 먼저 닫았는지 구분한다.
 */
class MessengerWebSocketShutdownTest {
	@Test void everyConnectionIsClosedWith1001BeforeTheServerStops() throws Exception {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
			.profiles("test")
			.run(
				"--server.port=0",
				"--spring.datasource.url=jdbc:h2:mem:messenger-websocket-shutdown",
				"--spring.datasource.driver-class-name=org.h2.Driver",
				"--messenger.realtime.allowed-origin=" + TestSocket.ALLOWED_ORIGIN)) {
			int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
			Member member = TestMembers.create(context.getBean(MemberRepository.class));
			String token = context.getBean(JwtProvider.class).createAccessToken(member.getEmail(), "USER");
			TestSocket first = TestSocket.connect(port, token);
			TestSocket second = TestSocket.connect(port, token);

			context.close();

			assertEquals(1001, first.awaitClose());
			assertEquals(1001, second.awaitClose());
			assertEquals(MessengerSessionRegistry.SHUTTING_DOWN.getReason(), first.closeReason);
			assertEquals(MessengerSessionRegistry.SHUTTING_DOWN.getReason(), second.closeReason);
		}
	}
}
