package com.devmozip.backend.message;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.service.ProjectChatService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프로젝트 단톡방 API(7.18~7.21). projects가 jsonb를 써서 PostgreSQL로만 확인한다.
 * 승인 팀원은 #48 병합 전이라 같은 결과의 행을 SQL로 넣는다(ProjectChatTestData).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProjectChatApiIntegrationTest extends MessengerApiTestSupport {
	static final String PROJECTS = "/api/v1/messages/projects";
	// MessengerApiTestSupport의 고정 시각(서울 2026-11-06 10:15)과 같은 날이다.
	static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
	static final String NOW_TEXT = "2026-11-06T10:15:00+09:00";

	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired ProjectChatService projectChats;
	@Autowired EntityManagerFactory entityManagerFactory;
	private ProjectChatTestData data;
	private int projectSequence;

	@BeforeEach void testData() {
		data = new ProjectChatTestData(jdbc);
	}

	private long project(Member host) {
		return project(host, TODAY.minusDays(5));
	}

	private long project(Member host, LocalDate recruitmentStart) {
		return data.project(host.getId(), "프로젝트" + (++projectSequence) + "-" + host.getTag(), recruitmentStart);
	}

	private String chat(long projectId) {
		return PROJECTS + "/" + projectId;
	}

	private JsonNode detail(Member who, long projectId) throws Exception {
		return json(getAs(who, chat(projectId)).andExpect(status().isOk()));
	}

	private long sendTo(Member who, long projectId, String body) throws Exception {
		return json(postAs(who, chat(projectId), Map.of("body", body)).andExpect(status().isCreated())).get("id").asLong();
	}

	private int deleteStatus(Member who, long projectId) throws Exception {
		return postAs(who, chat(projectId) + "/delete", Map.of()).andReturn().getResponse().getStatus();
	}

	private List<Long> listedIds(Member who) throws Exception {
		List<Long> ids = new ArrayList<>();
		json(getAs(who, CHATS + "?type=project").andExpect(status().isOk())).get("items").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	private long projectUnread(Member who) throws Exception {
		return json(getAs(who, PROJECTS + "/unread-count").andExpect(status().isOk())).get("count").asLong();
	}

	private void assertNotEnterable(Member who, long projectId) throws Exception {
		getAs(who, chat(projectId)).andExpect(status().isNotFound());
		postAs(who, chat(projectId), Map.of("body", "안녕")).andExpect(status().isNotFound());
		patchAs(who, chat(projectId) + "/read", Map.of("lastReadMessageId", 1)).andExpect(status().isNotFound());
		assertFalse(listedIds(who).contains(projectId));
	}

	// 인증

	@Test void everyEndpointRequiresLogin() throws Exception {
		mvc.perform(get(CHATS + "?type=project")).andExpect(status().isUnauthorized());
		mvc.perform(get(PROJECTS + "/unread-count")).andExpect(status().isUnauthorized());
		mvc.perform(get(PROJECTS + "/1")).andExpect(status().isUnauthorized());
		mvc.perform(post(PROJECTS + "/1").contentType("application/json").content("{\"body\":\"a\"}")).andExpect(status().isUnauthorized());
		mvc.perform(post(PROJECTS + "/1/delete")).andExpect(status().isUnauthorized());
		mvc.perform(patch(PROJECTS + "/1/read").contentType("application/json").content("{\"lastReadMessageId\":1}")).andExpect(status().isUnauthorized());
	}

	// 7.18·7.19 입장과 방 생성

	@Test void firstAccessCreatesTheRoomAndRowsForActiveMembers() throws Exception {
		Member host = member("host");
		Member teammate = member("teammate");
		long project = project(host);
		data.approve(project, teammate.getId());
		assertEquals(0, data.rooms(project));

		JsonNode body = detail(teammate, project);

		assertEquals(project, body.get("projectId").asLong());
		assertEquals("RECRUITING", body.get("status").asString());
		assertTrue(body.get("canSend").asBoolean());
		assertFalse(body.get("isHost").asBoolean());
		assertEquals(0, body.get("messages").size());
		assertTrue(body.get("nextBefore").isNull());
		JsonNode members = body.get("members");
		assertEquals(2, members.size());
		assertEquals(host.getTag(), members.get(0).get("tag").asString());
		assertEquals("HOST", members.get(0).get("role").asString());
		assertEquals(teammate.getTag(), members.get(1).get("tag").asString());
		assertEquals("PARTICIPANT", members.get(1).get("role").asString());
		assertEquals(NOW_TEXT, members.get(1).get("joinedAt").asString());
		assertTrue(members.get(1).get("leftAt").isNull());
		assertTrue(members.get(1).get("lastReadMessageId").isNull());
		assertFalse(members.get(0).get("me").asBoolean());
		assertTrue(members.get(1).get("me").asBoolean());
		assertTrue(members.get(0).get("id").asLong() < members.get(1).get("id").asLong());
		JsonNode seenByHost = detail(host, project);
		assertTrue(seenByHost.get("isHost").asBoolean());
		assertTrue(seenByHost.get("members").get(0).get("me").asBoolean());
		assertFalse(seenByHost.get("members").get(1).get("me").asBoolean());
		assertEquals(1, data.rooms(project));
		assertEquals(2, data.chatMemberRows(project));
	}

	@Test void concurrentFirstAccessCreatesOneRoomAndOneRowPerMember() throws Exception {
		for (int round = 0; round < 3; round++) {
			Member host = member("host");
			Member first = member("first");
			Member second = member("second");
			long project = project(host);
			data.approve(project, first.getId());
			data.approve(project, second.getId());
			List<Callable<Integer>> visits = new ArrayList<>();
			for (Member who : List.of(host, first, second, host, first, second)) {
				visits.add(() -> getAs(who, chat(project)).andReturn().getResponse().getStatus());
			}
			assertEquals(List.of(200), runTogether(visits).stream().distinct().toList());
			assertEquals(1, data.rooms(project));
			assertEquals(3, data.chatMemberRows(project));
		}
	}

	// 방이 이미 있으면 프로젝트를 잠그지 않으므로, 방 잠금이 새 팀원의 참여 행을 한 번만 만들게 해야 한다.
	@Test void concurrentAccessAfterApprovalsAddsEachMemberOnce() throws Exception {
		for (int round = 0; round < 3; round++) {
			Member host = member("host");
			long project = project(host);
			detail(host, project);
			List<Member> newcomers = List.of(member("new"), member("new"), member("new"));
			newcomers.forEach(newcomer -> data.approve(project, newcomer.getId()));
			List<Callable<Integer>> visits = new ArrayList<>();
			for (Member who : List.of(host, newcomers.get(0), newcomers.get(1), newcomers.get(2), host, newcomers.get(0))) {
				visits.add(() -> getAs(who, chat(project)).andReturn().getResponse().getStatus());
			}
			assertEquals(List.of(200), runTogether(visits).stream().distinct().toList());
			assertEquals(4, data.chatMemberRows(project));
		}
	}

	@Test void onlyActiveMembersOfStartedLiveProjectsCanEnter() throws Exception {
		Member host = member("host");
		Member stranger = member("stranger");
		Member cancelled = member("cancelled");
		long project = project(host);
		data.approve(project, cancelled.getId());
		data.leave(project, cancelled.getId());
		sendTo(host, project, "공지");

		assertNotEnterable(stranger, project);
		assertNotEnterable(cancelled, project);
		getAs(host, chat(Long.MAX_VALUE)).andExpect(status().isNotFound());

		long notStarted = project(host, TODAY.plusDays(1));
		assertNotEnterable(host, notStarted);
		assertEquals(0, data.rooms(notStarted));
		// 모집 시작일 당일부터 들어갈 수 있다.
		detail(host, project(host, TODAY));

		data.deleteProject(project);
		assertNotEnterable(host, project);
	}

	// 7.19 조회·전송, 7.20 읽음

	@Test void sendShowsTheSenderAndReadMovesThePosition() throws Exception {
		Member host = member("host");
		Member teammate = member("teammate");
		long project = project(host);
		data.approve(project, teammate.getId());

		JsonNode sent = json(postAs(teammate, chat(project), Map.of("body", "  안녕하세요  ")).andExpect(status().isCreated()));
		assertEquals("안녕하세요", sent.get("body").asString());
		assertTrue(sent.get("mine").asBoolean());
		assertEquals(teammate.getTag(), sent.get("sender").get("tag").asString());
		assertEquals(teammate.getNickname(), sent.get("sender").get("nickname").asString());
		assertEquals(NOW_TEXT, sent.get("createdAt").asString());
		long messageId = sent.get("id").asLong();

		JsonNode hostView = detail(host, project);
		JsonNode seenByHost = hostView.get("messages").get(0);
		assertFalse(seenByHost.get("mine").asBoolean());
		assertEquals(teammate.getTag(), seenByHost.get("sender").get("tag").asString());
		// 작성자의 memberId는 members[].id와 같아 태그가 없는 회원도 구분할 수 있다.
		assertEquals(hostView.get("members").get(1).get("id").asLong(), seenByHost.get("sender").get("memberId").asLong());
		assertEquals(sent.get("sender").get("memberId").asLong(), seenByHost.get("sender").get("memberId").asLong());
		assertEquals(1, projectUnread(host));
		assertEquals(0, projectUnread(teammate));

		patchAs(host, chat(project) + "/read", Map.of("lastReadMessageId", messageId)).andExpect(status().isNoContent());
		assertEquals(0, projectUnread(host));
		JsonNode members = detail(teammate, project).get("members");
		assertEquals(messageId, members.get(0).get("lastReadMessageId").asLong());
		assertEquals(messageId, members.get(1).get("lastReadMessageId").asLong());

		long directMessage = send(host, startChat(host, teammate), "1:1");
		patchAs(host, chat(project) + "/read", Map.of("lastReadMessageId", directMessage)).andExpect(status().isBadRequest());
		postRawAs(host, chat(project), "{}").andExpect(status().isBadRequest());
		mvc.perform(patch(chat(project) + "/read").header("Authorization", "Bearer " + jwt.createAccessToken(host.getEmail(), "USER"))
			.contentType("application/json").content("{}")).andExpect(status().isBadRequest());
	}

	@Test void messagesPageLikeDirectChats() throws Exception {
		Member host = member("host");
		long project = project(host);
		long first = sendTo(host, project, "1");
		long second = sendTo(host, project, "2");
		long third = sendTo(host, project, "3");

		getAs(host, chat(project) + "?size=2")
			.andExpect(jsonPath("$.messages[0].id").value(second))
			.andExpect(jsonPath("$.messages[1].id").value(third))
			.andExpect(jsonPath("$.nextBefore").value(second));
		getAs(host, chat(project) + "?before=" + second).andExpect(jsonPath("$.messages[0].id").value(first))
			.andExpect(jsonPath("$.nextBefore").isEmpty());
		getAs(host, chat(project) + "?after=" + first).andExpect(jsonPath("$.messages.length()").value(2));
	}

	@Test void invalidRequestsAreRejected() throws Exception {
		Member host = member("host");
		long project = project(host);
		getAs(host, chat(project) + "?before=1&after=1").andExpect(status().isBadRequest());
		getAs(host, chat(project) + "?size=0").andExpect(status().isBadRequest());
		getAs(host, chat(project) + "?size=101").andExpect(status().isBadRequest());
		getAs(host, chat(project) + "?before=abc").andExpect(status().isBadRequest());
		getAs(host, CHATS + "?type=project&size=0").andExpect(status().isBadRequest());
		getAs(host, CHATS + "?type=group").andExpect(status().isBadRequest());
		postAs(host, chat(project), Map.of("body", "   ")).andExpect(status().isBadRequest());
		postAs(host, chat(project), Map.of("body", "가".repeat(2001))).andExpect(status().isBadRequest());
		postAs(host, chat(project), Map.of("body", "a\u0000b")).andExpect(status().isBadRequest());
		assertEquals(0, data.messageRows(project));
	}

	// 11.1 목록

	@Test void listIncludesProjectsWithoutARoomAndKeepsTypesApart() throws Exception {
		Member host = member("host");
		Member teammate = member("teammate");
		long older = project(host);
		data.approve(older, teammate.getId());
		long newer = project(host);
		sendTo(teammate, older, "안녕");

		JsonNode items = json(getAs(host, CHATS + "?type=project")).get("items");
		assertEquals(List.of(newer, older), listedIds(host));
		assertEquals(0, data.rooms(newer));
		assertEquals(1, items.get(0).get("memberCount").asLong());
		assertEquals(0, items.get(0).get("unreadCount").asLong());
		assertEquals(2, items.get(1).get("memberCount").asLong());
		assertEquals(1, items.get(1).get("unreadCount").asLong());
		assertEquals("RECRUITING", items.get(1).get("status").asString());

		// 프로젝트 채팅은 1:1 목록과 1:1 안 읽은 수에 섞이지 않는다.
		getAs(host, CHATS).andExpect(jsonPath("$.items.length()").value(0));
		getAs(host, CHATS + "/unread-count").andExpect(jsonPath("$.count").value(0));

		String title = jdbc.queryForObject("SELECT title FROM projects WHERE id = ?", String.class, older);
		mvc.perform(get(CHATS).param("type", "project").param("q", title.toUpperCase())
				.header("Authorization", "Bearer " + jwt.createAccessToken(host.getEmail(), "USER")))
			.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(older));
		getAs(host, CHATS + "?type=project&size=1")
			.andExpect(jsonPath("$.items[0].id").value(newer)).andExpect(jsonPath("$.nextCursor").value(newer));
		getAs(host, CHATS + "?type=project&size=1&cursor=" + newer)
			.andExpect(jsonPath("$.items[0].id").value(older)).andExpect(jsonPath("$.nextCursor").isEmpty());
	}

	@Test void listQueryCountDoesNotGrowWithProjects() throws Exception {
		Member host = member("host");
		sendTo(host, project(host), "메시지");
		long one = listStatementCount(host, 1);
		for (int i = 0; i < 4; i++) {
			Member teammate = member("teammate");
			long project = project(host);
			data.approve(project, teammate.getId());
			sendTo(teammate, project, "메시지");
		}
		assertEquals(one, listStatementCount(host, 5));
	}

	private long listStatementCount(Member who, int expected) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		getAs(who, CHATS + "?type=project").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(expected));
		return statistics.getPrepareStatementCount();
	}

	// 승인·취소 반영

	@Test void approvalAndCancellationAreReflectedOnTheNextAccess() throws Exception {
		Member host = member("host");
		Member newcomer = member("newcomer");
		long project = project(host);
		long beforeApproval = sendTo(host, project, "승인 전 메시지");
		data.approve(project, newcomer.getId());

		// 조회에서는 쓰지 않으므로 다음 접근 전까지는 0이다.
		assertEquals(0, projectUnread(newcomer));
		JsonNode body = detail(newcomer, project);
		assertEquals(beforeApproval, body.get("messages").get(0).get("id").asLong());
		assertEquals(1, projectUnread(newcomer));

		data.leave(project, newcomer.getId());
		// 아무도 접근하지 않아 참여 행에 퇴장이 반영되기 전에도 읽음 처리와 삭제는 막고, 멤버였다는 사실도 드러내지 않는다.
		patchAs(newcomer, chat(project) + "/read", Map.of("lastReadMessageId", beforeApproval)).andExpect(status().isNotFound());
		assertEquals(404, deleteStatus(newcomer, project));
		JsonNode members = detail(host, project).get("members");
		assertEquals(newcomer.getTag(), members.get(1).get("tag").asString());
		assertEquals(NOW_TEXT, members.get(1).get("leftAt").asString());
		assertNotEnterable(newcomer, project);
		assertEquals(0, projectUnread(newcomer));
		getAs(host, CHATS + "?type=project").andExpect(jsonPath("$.items[0].memberCount").value(1));
		assertEquals(2, data.chatMemberRows(project));
	}

	@Test void excludedMemberCannotEnterEvenWhileStillAProjectMember() throws Exception {
		Member host = member("host");
		Member excluded = member("excluded");
		long project = project(host);
		data.approve(project, excluded.getId());
		sendTo(excluded, project, "안녕");
		sendTo(host, project, "안녕하세요");

		projectChats.excludeMember(project, excluded.getId());

		assertNotEnterable(excluded, project);
		assertEquals(0, projectUnread(excluded));
		assertEquals(NOW_TEXT, detail(host, project).get("members").get(1).get("leftAt").asString());
		getAs(host, CHATS + "?type=project").andExpect(jsonPath("$.items[0].memberCount").value(1));
	}

	@Test void exclusionBeforeTheRoomExistsIsKept() throws Exception {
		Member host = member("host");
		Member excluded = member("excluded");
		long project = project(host);
		data.approve(project, excluded.getId());

		projectChats.excludeMember(project, excluded.getId());

		assertEquals(1, data.rooms(project));
		assertNotEnterable(excluded, project);
		detail(host, project);
		assertNotEnterable(excluded, project);
		assertEquals(2, data.chatMemberRows(project));
	}

	// 7.21 안 읽은 수

	@Test void unreadTotalSkipsDeletedLeftAndExcludedRooms() throws Exception {
		Member me = member("me");
		Member host = member("host");
		long counted = project(host);
		long deleted = project(host);
		long excludedFrom = project(host);
		long cancelled = project(host);
		for (long project : List.of(counted, deleted, excludedFrom, cancelled)) {
			data.approve(project, me.getId());
			detail(me, project);
			sendTo(host, project, "하나");
			sendTo(host, project, "둘");
		}
		assertEquals(8, projectUnread(me));

		data.status(deleted, "COMPLETED");
		assertEquals(204, deleteStatus(host, deleted));
		projectChats.excludeMember(excludedFrom, me.getId());
		data.leave(cancelled, me.getId());

		assertEquals(2, projectUnread(me));
		assertEquals(List.of(counted), listedIds(me));
	}

	// 종료와 삭제

	@Test void completedProjectIsReadOnly() throws Exception {
		Member host = member("host");
		long project = project(host);
		sendTo(host, project, "마지막 메시지");
		data.status(project, "COMPLETED");

		JsonNode body = detail(host, project);
		assertFalse(body.get("canSend").asBoolean());
		assertEquals("COMPLETED", body.get("status").asString());
		assertEquals(1, body.get("messages").size());
		postAs(host, chat(project), Map.of("body", "안녕")).andExpect(status().isConflict());
	}

	@Test void onlyTheHostDeletesAfterCompletionAndTheRoomStaysDeleted() throws Exception {
		Member host = member("host");
		Member teammate = member("teammate");
		Member stranger = member("stranger");
		long project = project(host);
		data.approve(project, teammate.getId());
		long messageId = sendTo(teammate, project, "남는 메시지");

		assertEquals(409, deleteStatus(host, project));
		data.status(project, "COMPLETED");
		assertEquals(403, deleteStatus(teammate, project));
		assertEquals(404, deleteStatus(stranger, project));
		assertEquals(204, deleteStatus(host, project));

		assertNotEnterable(host, project);
		assertNotEnterable(teammate, project);
		patchAs(host, chat(project) + "/read", Map.of("lastReadMessageId", messageId)).andExpect(status().isNotFound());
		assertEquals(0, projectUnread(host));
		assertEquals(1, data.messageRows(project));
		assertEquals(2, data.chatMemberRows(project));
		// 다시 삭제해도 처음 삭제한 시각을 유지하고, 방을 새로 만들지 않는다.
		assertEquals(204, deleteStatus(host, project));
		assertEquals(1, data.rooms(project));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM chat_rooms WHERE project_id = ? AND deleted_at IS NOT NULL", Integer.class, project));
	}

	@Test void deletingAProjectWithoutARoomPreventsCreatingOne() throws Exception {
		Member host = member("host");
		long project = project(host);
		data.status(project, "COMPLETED");

		assertEquals(204, deleteStatus(host, project));

		assertEquals(1, data.rooms(project));
		assertNotEnterable(host, project);
		assertEquals(1, data.rooms(project));
		assertEquals(0, data.chatMemberRows(project));
	}

	@Test void deletedProjectCannotBeDeletedOrEntered() throws Exception {
		Member host = member("host");
		long project = project(host);
		data.status(project, "COMPLETED");
		data.deleteProject(project);
		assertEquals(404, deleteStatus(host, project));
		assertNotEnterable(host, project);
	}
}
