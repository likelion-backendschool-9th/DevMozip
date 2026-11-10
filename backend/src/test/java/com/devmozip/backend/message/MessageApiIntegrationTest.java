package com.devmozip.backend.message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import com.devmozip.backend.entity.Member;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MessageApiIntegrationTest extends MessageApiTests {
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired EntityManagerFactory entityManagerFactory;

	@Test void concurrentStartsFromBothSidesCreateOneRoom() throws Exception {
		for (int round = 0; round < 5; round++) {
			Member alice = member("alice");
			Member bob = member("bob");
			List<Callable<Long>> starts = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				Member from = i % 2 == 0 ? alice : bob;
				Member to = i % 2 == 0 ? bob : alice;
				starts.add(() -> startChat(from, to));
			}
			assertEquals(1, new HashSet<>(runTogether(starts)).size());
			assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM chat_members WHERE user_id = ?", Integer.class, alice.getId()));
			assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM chat_members WHERE user_id = ?", Integer.class, bob.getId()));
		}
	}

	// 시각을 고정해 같은 순간의 전송을 만든다. 잠금이 없으면 sent_at이 겹치거나 ID 순서와 어긋난다.
	@Test void concurrentSendsKeepIdAndSentAtInTheSameOrder() throws Exception {
		Member alice = member("alice");
		Member bob = member("bob");
		long room = startChat(alice, bob);
		List<Callable<Long>> sends = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			Member from = i % 2 == 0 ? alice : bob;
			String body = "메시지" + i;
			sends.add(() -> send(from, room, body));
		}
		assertEquals(16, new HashSet<>(runTogether(sends)).size());

		List<LocalDateTime> sentAt = jdbc.queryForList(
			"SELECT m.sent_at FROM messages m JOIN chat_members c ON c.id = m.chat_member_id WHERE c.room_id = ? ORDER BY m.id", LocalDateTime.class, room);
		assertEquals(16, sentAt.size());
		for (int i = 1; i < sentAt.size(); i++) {
			assertTrue(sentAt.get(i - 1).isBefore(sentAt.get(i)), sentAt.toString());
		}
	}

	@Test void concurrentReadsNeverMovePositionBackward() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			ids.add(send(peer, room, "메시지" + i));
		}
		List<Callable<Integer>> reads = new ArrayList<>();
		for (long id : ids) {
			reads.add(() -> patchAs(me, CHATS + "/" + room + "/read", Map.of("lastReadMessageId", id)).andReturn().getResponse().getStatus());
		}
		assertEquals(List.of(204), runTogether(reads).stream().distinct().toList());
		getAs(peer, CHATS + "/" + room).andExpect(jsonPath("$.peerLastReadMessageId").value(ids.getLast()));
	}

	// 방 수가 늘어도 조회 횟수가 그대로여야 한다.
	@Test void listQueryCountDoesNotGrowWithRooms() throws Exception {
		Member me = member("me");
		send(me, startChat(me, member("peer")), "메시지");
		long oneRoom = listStatementCount(me, 1);
		for (int i = 0; i < 4; i++) {
			send(me, startChat(me, member("peer")), "메시지");
		}
		assertEquals(oneRoom, listStatementCount(me, 5));
	}

	private long listStatementCount(Member me, int expectedRooms) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		getAs(me, CHATS).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(expectedRooms));
		return statistics.getPrepareStatementCount();
	}
}
