package com.devmozip.backend.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** 1:1 방의 입력 중 표시(11.9). 간격과 캐시 시간을 줄여 확인한다. 프로젝트 방은 PostgreSQL 테스트에서 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.datasource.url=jdbc:h2:mem:messenger-typing",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"messenger.realtime.allowed-origin=" + TestSocket.ALLOWED_ORIGIN,
	"messenger.realtime.typing-interval=400ms",
	"messenger.realtime.typing-access-cache=200ms"
})
@ActiveProfiles("test")
class MessengerTypingTest {
	@LocalServerPort int port;
	@Autowired MessageService messages;
	@Autowired MemberRepository members;
	@Autowired JwtProvider jwt;
	@Autowired JdbcTemplate jdbc;
	private final List<TestSocket> opened = new ArrayList<>();
	private Member alice;
	private Member bob;
	private long room;
	private TestSocket aliceSocket;
	private TestSocket bobSocket;

	@BeforeEach void openChat() throws Exception {
		alice = TestMembers.create(members);
		bob = TestMembers.create(members);
		room = messages.startDirectChat(alice.getId(), bob.getTag()).id();
		aliceSocket = connect(alice);
		bobSocket = connect(bob);
	}

	@AfterEach void closeSockets() {
		opened.forEach(TestSocket::abort);
	}

	private TestSocket connect(Member member) throws Exception {
		TestSocket socket = TestSocket.connect(port, jwt.createAccessToken(member.getEmail(), "USER"));
		opened.add(socket);
		return socket;
	}

	private static String typing(long roomId) {
		return "{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":" + roomId + "}";
	}

	private static String next(TestSocket socket) throws Exception {
		return socket.frames.poll(5, TimeUnit.SECONDS);
	}

	private static void assertNothing(TestSocket socket) throws Exception {
		assertNull(socket.frames.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test void peerReceivesTypingButTheSendersTabsDoNot() throws Exception {
		TestSocket aliceOtherTab = connect(alice);

		aliceSocket.sendText(typing(room));

		assertEquals(typing(room), next(bobSocket));
		assertNothing(aliceSocket);
		assertNothing(aliceOtherTab);
		assertEquals(0, jdbc.queryForObject(
			"SELECT count(*) FROM messages m JOIN chat_members c ON c.id = m.chat_member_id WHERE c.room_id = ?", Integer.class, room));
	}

	@Test void framesWithinTheIntervalAreDropped() throws Exception {
		aliceSocket.sendText(typing(room));
		aliceSocket.sendText(typing(room));
		aliceSocket.sendText(typing(room));
		assertEquals(typing(room), next(bobSocket));
		assertNothing(bobSocket);

		Thread.sleep(450);
		aliceSocket.sendText(typing(room));
		assertEquals(typing(room), next(bobSocket));
	}

	@Test void intervalIsSharedByTheMembersTabs() throws Exception {
		TestSocket aliceOtherTab = connect(alice);
		aliceSocket.sendText(typing(room));
		aliceOtherTab.sendText(typing(room));
		assertEquals(typing(room), next(bobSocket));
		assertNothing(bobSocket);
	}

	@Test void eachRoomAndMemberHasItsOwnInterval() throws Exception {
		Member carol = TestMembers.create(members);
		long otherRoom = messages.startDirectChat(alice.getId(), carol.getTag()).id();
		TestSocket carolSocket = connect(carol);

		aliceSocket.sendText(typing(room));
		aliceSocket.sendText(typing(otherRoom));
		bobSocket.sendText(typing(room));

		assertEquals(typing(room), next(bobSocket));
		assertEquals(typing(otherRoom), next(carolSocket));
		assertEquals(typing(room), next(aliceSocket));
	}

	@Test void roomTheMemberIsNotInIsIgnoredWithoutClosing() throws Exception {
		TestSocket stranger = connect(TestMembers.create(members));

		stranger.sendText(typing(room));
		stranger.sendText(typing(Long.MAX_VALUE));

		assertNothing(bobSocket);
		assertNothing(aliceSocket);
		assertFalse(stranger.isClosed());
	}

	@Test void suspendedPeerDoesNotReceiveTyping() throws Exception {
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", bob.getId());

		aliceSocket.sendText(typing(room));

		assertNothing(bobSocket);
		assertFalse(aliceSocket.isClosed());
	}

	@Test void accessChangeIsReflectedAfterTheCacheExpires() throws Exception {
		aliceSocket.sendText(typing(room));
		assertEquals(typing(room), next(bobSocket));

		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", bob.getId());
		Thread.sleep(450);
		aliceSocket.sendText(typing(room));

		assertNothing(bobSocket);
	}

	// local(H2)에는 projects 테이블이 없어 조회가 실패한다. 입력 중 표시는 유실을 허용하므로 연결을 유지해야 한다.
	@Test void failedLookupKeepsTheConnection() throws Exception {
		aliceSocket.sendText("{\"type\":\"typing\",\"roomType\":\"project\",\"roomId\":1}");

		assertNothing(bobSocket);
		assertFalse(aliceSocket.isClosed());
		aliceSocket.sendText(typing(room));
		assertEquals(typing(room), next(bobSocket));
	}
}
