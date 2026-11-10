package com.devmozip.backend.websocket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.config.MessengerConfiguration;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.message.ProjectChatTestData;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.service.MessageService;
import com.devmozip.backend.service.ProjectChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/** 프로젝트 단톡방의 실시간 전달(7.22)과 입력 중 표시(7.23). roomId는 프로젝트 ID다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"messenger.realtime.allowed-origin=" + TestSocket.ALLOWED_ORIGIN,
	"messenger.realtime.typing-interval=100ms",
	// 상태 변경을 바로 확인하도록 참여 여부를 기억하지 않는다.
	"messenger.realtime.typing-access-cache=0s"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProjectChatRealtimeIntegrationTest {
	private static final String BODY = "본문은-신호에-없어야-한다";

	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@LocalServerPort int port;
	@Autowired ProjectChatService projectChats;
	@Autowired MessageService directChats;
	@Autowired MemberRepository members;
	@Autowired JwtProvider jwt;
	@Autowired JdbcTemplate jdbc;
	private final List<TestSocket> opened = new ArrayList<>();
	private ProjectChatTestData data;
	private Member host;
	private Member teammate;
	private long project;
	private TestSocket hostSocket;
	private TestSocket teammateSocket;
	private TestSocket strangerSocket;

	@BeforeEach void openRoom() throws Exception {
		data = new ProjectChatTestData(jdbc);
		host = TestMembers.create(members);
		teammate = TestMembers.create(members);
		project = newProject(host, teammate);
		hostSocket = connect(host);
		teammateSocket = connect(teammate);
		strangerSocket = connect(TestMembers.create(members));
	}

	@AfterEach void closeSockets() {
		opened.forEach(TestSocket::abort);
	}

	private long newProject(Member owner, Member... teammates) {
		long id = data.project(owner.getId(), "실시간 프로젝트", LocalDate.now(MessengerConfiguration.ZONE).minusDays(1));
		for (Member member : teammates) {
			data.approve(id, member.getId());
		}
		projectChats.getProjectChat(owner.getId(), id, null, null, null);
		return id;
	}

	private TestSocket connect(Member member) throws Exception {
		TestSocket socket = TestSocket.connect(port, jwt.createAccessToken(member.getEmail(), "USER"));
		opened.add(socket);
		return socket;
	}

	private long send(Member from, long projectId) {
		return projectChats.sendProjectMessage(from.getId(), projectId, BODY).id();
	}

	private static String created(long projectId, long messageId) {
		return "{\"type\":\"message.created\",\"roomType\":\"project\",\"roomId\":" + projectId + ",\"messageId\":" + messageId + "}";
	}

	private static String typing(long projectId) {
		return "{\"type\":\"typing\",\"roomType\":\"project\",\"roomId\":" + projectId + "}";
	}

	private static String next(TestSocket socket) throws Exception {
		return socket.frames.poll(5, TimeUnit.SECONDS);
	}

	private static void assertNothing(TestSocket socket) throws Exception {
		assertNull(socket.frames.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test void messageSignalReachesEveryCurrentMemberAndTheirTabs() throws Exception {
		TestSocket teammateOtherTab = connect(teammate);

		long id = send(teammate, project);

		assertEquals(created(project, id), next(hostSocket));
		assertEquals(created(project, id), next(teammateSocket));
		assertEquals(created(project, id), next(teammateOtherTab));
		assertNothing(strangerSocket);
	}

	@Test void leftAndExcludedMembersStopReceiving() throws Exception {
		Member leaver = TestMembers.create(members);
		Member excluded = TestMembers.create(members);
		data.approve(project, leaver.getId());
		data.approve(project, excluded.getId());
		projectChats.getProjectChat(leaver.getId(), project, null, null, null);
		projectChats.getProjectChat(excluded.getId(), project, null, null, null);
		TestSocket leaverSocket = connect(leaver);
		TestSocket excludedSocket = connect(excluded);

		data.leave(project, leaver.getId());
		projectChats.excludeMember(project, excluded.getId());
		// 참여 행에 퇴장이 반영되기 전에도 나간 회원의 입력 중 신호는 전달하지 않는다.
		leaverSocket.sendText(typing(project));
		excludedSocket.sendText(typing(project));
		assertNothing(hostSocket);
		long id = send(host, project);

		assertEquals(created(project, id), next(teammateSocket));
		assertNothing(leaverSocket);
		assertNothing(excludedSocket);
	}

	@Test void readSignalNamesTheReaderAndIsSentOnlyWhenThePositionMoves() throws Exception {
		long id = send(teammate, project);
		next(hostSocket);
		next(teammateSocket);

		projectChats.markProjectChatRead(host.getId(), project, id);

		String read = "{\"type\":\"message.read\",\"roomType\":\"project\",\"roomId\":" + project
			+ ",\"lastReadMessageId\":" + id + ",\"memberTag\":\"" + host.getTag() + "\"}";
		assertEquals(read, next(hostSocket));
		assertEquals(read, next(teammateSocket));
		projectChats.markProjectChatRead(host.getId(), project, id);
		assertNothing(teammateSocket);
	}

	@Test void typingReachesOtherMembersWithTheNickname() throws Exception {
		TestSocket teammateOtherTab = connect(teammate);

		teammateSocket.sendText(typing(project));

		assertEquals("{\"type\":\"typing\",\"roomType\":\"project\",\"roomId\":" + project + ",\"nickname\":\"" + teammate.getNickname() + "\"}",
			next(hostSocket));
		assertNothing(teammateOtherTab);
		assertNothing(strangerSocket);
	}

	@Test void typingIsIgnoredForStrangersAndClosedRooms() throws Exception {
		strangerSocket.sendText(typing(project));
		assertNothing(hostSocket);

		data.status(project, "COMPLETED");
		teammateSocket.sendText(typing(project));
		assertNothing(hostSocket);

		projectChats.deleteProjectChat(host.getId(), project);
		Thread.sleep(150);
		teammateSocket.sendText(typing(project));
		assertNothing(hostSocket);
		assertFalse(teammateSocket.isClosed());
		assertFalse(strangerSocket.isClosed());
	}

	// 대화방 수와 무관하게 연결 하나로 모든 방의 신호를 받는다.
	@Test void oneConnectionReceivesSignalsFromEveryRoom() throws Exception {
		Set<String> expected = new HashSet<>();
		long direct = directChats.startDirectChat(teammate.getId(), host.getTag()).id();
		long directMessage = directChats.sendDirectMessage(teammate.getId(), direct, BODY).id();
		expected.add("{\"type\":\"message.created\",\"roomType\":\"direct\",\"roomId\":" + direct + ",\"messageId\":" + directMessage + "}");
		for (int i = 0; i < 6; i++) {
			Member other = TestMembers.create(members);
			long room = newProject(other, host);
			expected.add(created(room, send(other, room)));
		}
		expected.add(created(project, send(teammate, project)));

		Set<String> received = new HashSet<>();
		for (int i = 0; i < expected.size(); i++) {
			received.add(next(hostSocket));
		}
		assertEquals(expected, received);
		assertEquals(1, opened.stream().filter(socket -> socket == hostSocket).count());
	}
}
