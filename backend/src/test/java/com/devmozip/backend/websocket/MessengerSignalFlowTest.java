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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** 메시지 서비스부터 실제 WebSocket 클라이언트까지 확인한다. Redis 없는 설정(local과 같다)이다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.datasource.url=jdbc:h2:mem:messenger-signal-flow",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"messenger.realtime.allowed-origin=" + TestSocket.ALLOWED_ORIGIN
})
@ActiveProfiles("test")
class MessengerSignalFlowTest {
	private static final String BODY = "본문은-신호에-없어야-한다";

	@LocalServerPort int port;
	@Autowired MessageService messages;
	@Autowired MessengerSignalPublisher publisher;
	@Autowired MemberRepository members;
	@Autowired JwtProvider jwt;
	@Autowired TransactionTemplate transaction;
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

	private long send(Member from, String body) {
		return messages.sendDirectMessage(from.getId(), room, body).id();
	}

	private String created(long messageId) {
		return "{\"type\":\"message.created\",\"roomType\":\"direct\",\"roomId\":" + room + ",\"messageId\":" + messageId + "}";
	}

	private String read(long messageId) {
		return "{\"type\":\"message.read\",\"roomType\":\"direct\",\"roomId\":" + room + ",\"lastReadMessageId\":" + messageId + "}";
	}

	private static String next(TestSocket socket) throws Exception {
		return socket.frames.poll(5, TimeUnit.SECONDS);
	}

	// 설정이 빠지면 Redis 발행이 실패한 뒤 직접 전달하는 경로로 통과해 버린다.
	@Test void runsWithoutRedis() {
		assertInstanceOf(LocalMessengerSignalPublisher.class, publisher);
	}

	@Test void newMessageSignalsBothParticipantsWithoutTheBody() throws Exception {
		long id = send(alice, BODY);

		String toPeer = next(bobSocket);
		String toSender = next(aliceSocket);
		assertEquals(created(id), toPeer);
		assertEquals(created(id), toSender);
		assertFalse(toPeer.contains(BODY));
	}

	@Test void everyTabOfBothParticipantsIsSignaled() throws Exception {
		TestSocket aliceOtherTab = connect(alice);
		TestSocket bobOtherTab = connect(bob);

		long id = send(alice, BODY);

		assertEquals(created(id), next(aliceSocket));
		assertEquals(created(id), next(aliceOtherTab));
		assertEquals(created(id), next(bobSocket));
		assertEquals(created(id), next(bobOtherTab));
	}

	@Test void memberOutsideTheRoomReceivesNothing() throws Exception {
		TestSocket stranger = connect(TestMembers.create(members));

		long id = send(alice, BODY);

		assertEquals(created(id), next(bobSocket));
		assertNull(stranger.frames.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test void readSignalsBothParticipants() throws Exception {
		long id = send(alice, BODY);
		assertEquals(created(id), next(aliceSocket));
		assertEquals(created(id), next(bobSocket));

		messages.markDirectChatRead(bob.getId(), room, id);

		assertEquals(read(id), next(aliceSocket));
		assertEquals(read(id), next(bobSocket));
	}

	// 신호 순서는 같은 스레드가 보장한다. 뒤이은 메시지가 먼저 도착하면 그 앞에 읽음 신호가 없었던 것이다.
	@Test void readThatDoesNotMoveThePositionSendsNothing() throws Exception {
		long first = send(alice, BODY);
		long second = send(alice, BODY);
		messages.markDirectChatRead(bob.getId(), room, second);
		assertEquals(created(first), next(aliceSocket));
		assertEquals(created(second), next(aliceSocket));
		assertEquals(read(second), next(aliceSocket));

		messages.markDirectChatRead(bob.getId(), room, second);
		messages.markDirectChatRead(bob.getId(), room, first);
		long third = send(alice, BODY);

		assertEquals(created(third), next(aliceSocket));
	}

	@Test void sendingDoesNotAlsoSendAReadSignal() throws Exception {
		long first = send(alice, BODY);
		long second = send(bob, BODY);

		assertEquals(created(first), next(aliceSocket));
		assertEquals(created(second), next(aliceSocket));
		assertNull(aliceSocket.frames.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test void rolledBackMessageSendsNothing() throws Exception {
		transaction.executeWithoutResult(status -> {
			send(alice, BODY);
			status.setRollbackOnly();
		});
		long committed = send(alice, BODY);

		assertEquals(created(committed), next(aliceSocket));
		assertEquals(created(committed), next(bobSocket));
	}

	@Test void signalArrivesOnlyAfterTheTransactionCommits() throws Exception {
		long id = transaction.execute(status -> {
			long sent = send(alice, BODY);
			try {
				assertNull(aliceSocket.frames.poll(300, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
			return sent;
		});

		assertEquals(created(id), next(aliceSocket));
	}

	@Test void rejectedMessageSendsNothing() throws Exception {
		assertThrows(ResponseStatusException.class, () -> send(alice, "   "));
		long committed = send(alice, BODY);

		assertEquals(created(committed), next(aliceSocket));
	}
}
