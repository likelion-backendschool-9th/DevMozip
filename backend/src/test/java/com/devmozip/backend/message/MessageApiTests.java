package com.devmozip.backend.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.entity.ChatRoom;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.ChatRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL과 local(H2) 프로필이 같은 API 동작을 하는지 확인한다. */
abstract class MessageApiTests extends MessengerApiTestSupport {
	@Autowired ChatRoomRepository rooms;

	// 인증

	@Test void everyEndpointRequiresLogin() throws Exception {
		mvc.perform(get(CHATS)).andExpect(status().isUnauthorized());
		mvc.perform(get(CHATS + "/unread-count")).andExpect(status().isUnauthorized());
		mvc.perform(get(CHATS + "/1")).andExpect(status().isUnauthorized());
		mvc.perform(post(CHATS).contentType("application/json").content("{\"targetSlug\":\"a\"}")).andExpect(status().isUnauthorized());
		mvc.perform(post(CHATS + "/1").contentType("application/json").content("{\"body\":\"a\"}")).andExpect(status().isUnauthorized());
		mvc.perform(patch(CHATS + "/1/read").contentType("application/json").content("{\"lastReadMessageId\":1}")).andExpect(status().isUnauthorized());
	}

	@Test void tokenOfUnknownMemberIsRejected() throws Exception {
		mvc.perform(get(CHATS).header("Authorization", "Bearer " + jwt.createAccessToken("nobody@devmozip.test", "USER")))
			.andExpect(status().isUnauthorized());
	}

	// 11.5 대화 시작

	@Test void startKeepsOneRoomPerPairFromEitherSide() throws Exception {
		Member alice = member("alice");
		Member bob = member("bob");
		long room = startChat(alice, bob);
		assertEquals(room, startChat(alice, bob));
		assertEquals(room, startChat(bob, alice));
		assertNotEquals(room, startChat(alice, member("carol")));
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM chat_members WHERE room_id = ?", Integer.class, room));
	}

	@Test void startRejectsInvalidTargets() throws Exception {
		Member me = member("me");
		Member suspended = member("suspended");
		suspend(suspended);
		Member noRoomYet = member("target");
		postAs(me, CHATS, Map.of()).andExpect(status().isBadRequest());
		postAs(me, CHATS, Map.of("targetSlug", " ")).andExpect(status().isBadRequest());
		postRawAs(me, CHATS, "{\"targetSlug\":123}").andExpect(status().isBadRequest());
		postAs(me, CHATS, Map.of("targetSlug", me.getTag())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
		postAs(me, CHATS, Map.of("targetSlug", "no_such_tag")).andExpect(status().isNotFound());
		postAs(me, CHATS, Map.of("targetSlug", suspended.getTag())).andExpect(status().isNotFound());
		postAs(me, CHATS, Map.of("targetSlug", "bad\u0000tag")).andExpect(status().isNotFound());
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM chat_members WHERE user_id = ?", Integer.class, me.getId()));
		assertTrue(startChat(me, noRoomYet) > 0);
	}

	// 11.1 목록

	@Test void listShowsOnlyMyRoomsWithMessagesNewestFirst() throws Exception {
		Member me = member("me");
		Member older = member("older");
		Member newer = member("newer");
		Member silent = member("silent");
		long olderRoom = startChat(me, older);
		long newerRoom = startChat(me, newer);
		startChat(me, silent);
		long otherRoom = startChat(older, newer);
		send(older, otherRoom, "남의 대화");
		long firstInOlder = send(older, olderRoom, "오래된 대화");
		long firstInNewer = send(me, newerRoom, "내가 보낸 메시지");
		assertTrue(firstInNewer > firstInOlder);

		getAs(me, CHATS).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.nextCursor").isEmpty())
			.andExpect(jsonPath("$.items[0].id").value(newerRoom))
			.andExpect(jsonPath("$.items[0].peer.tag").value(newer.getTag()))
			.andExpect(jsonPath("$.items[0].peer.nickname").value(newer.getNickname()))
			.andExpect(jsonPath("$.items[0].peer.jobCategories[0]").value("백엔드 개발자"))
			.andExpect(jsonPath("$.items[0].peer.collaborationStatus").value("AVAILABLE"))
			.andExpect(jsonPath("$.items[0].lastMessage.id").value(firstInNewer))
			.andExpect(jsonPath("$.items[0].lastMessage.body").value("내가 보낸 메시지"))
			.andExpect(jsonPath("$.items[0].lastMessage.mine").value(true))
			.andExpect(jsonPath("$.items[0].unreadCount").value(0))
			.andExpect(jsonPath("$.items[1].id").value(olderRoom))
			.andExpect(jsonPath("$.items[1].lastMessage.mine").value(false))
			.andExpect(jsonPath("$.items[1].unreadCount").value(1));

		// 오래된 방에 새 메시지가 오면 맨 위로 올라온다.
		send(older, olderRoom, "다시 왔어요");
		getAs(me, CHATS).andExpect(jsonPath("$.items[0].id").value(olderRoom)).andExpect(jsonPath("$.items[0].unreadCount").value(2));
	}

	@Test void listSearchesPeerNicknameAsPlainText() throws Exception {
		Member me = member("me");
		Member target = member("Target");
		Member other = member("zzz");
		send(me, startChat(me, target), "안녕하세요");
		send(me, startChat(me, other), "안녕하세요");

		searchAs(me, target.getNickname().toLowerCase()).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].peer.tag").value(target.getTag()));
		searchAs(me, "%").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
		searchAs(me, "_").andExpect(jsonPath("$.items.length()").value(0));
		searchAs(me, "없는닉네임").andExpect(jsonPath("$.items.length()").value(0));
		searchAs(me, "  ").andExpect(jsonPath("$.items.length()").value(2));
		searchAs(me, "\u0000").andExpect(status().isBadRequest());
	}

	@Test void listKeepsPeerWithoutNicknameUntilSearching() throws Exception {
		Member me = member("me");
		Member noNickname = members.save(Member.builder().email("no-nickname" + System.nanoTime() + "@devmozip.test").tag("no_nickname" + System.nanoTime()).status(Member.MemberStatus.ACTIVE).build());
		send(me, startChat(me, noNickname), "안녕하세요");
		getAs(me, CHATS).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].peer.nickname").isEmpty())
			.andExpect(jsonPath("$.items[0].peer.jobCategories.length()").value(0))
			.andExpect(jsonPath("$.items[0].peer.collaborationStatus").isEmpty());
		searchAs(me, "닉네임").andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test void listPagesWithCursor() throws Exception {
		Member me = member("me");
		List<Long> rooms = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			long room = startChat(me, member("peer"));
			send(me, room, "메시지");
			rooms.add(room);
		}
		JsonNode first = json(getAs(me, CHATS + "?size=2").andExpect(jsonPath("$.items.length()").value(2)));
		assertEquals(rooms.get(2), first.at("/items/0/id").asLong());
		assertEquals(rooms.get(1), first.at("/items/1/id").asLong());
		long cursor = first.get("nextCursor").asLong();
		getAs(me, CHATS + "?size=2&cursor=" + cursor)
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(rooms.get(0)))
			.andExpect(jsonPath("$.nextCursor").isEmpty());
	}

	@ParameterizedTest
	// type=project는 12단계에서 추가했다. 동작은 ProjectChatApiIntegrationTest에서 확인한다.
	@ValueSource(strings = {"type=group", "type=unknown", "size=0", "size=101", "size=abc", "cursor=abc"})
	void listRejectsInvalidParameters(String parameter) throws Exception {
		getAs(member("me"), CHATS + "?" + parameter).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
	}

	// 11.7 안 읽은 수

	@Test void unreadCountSkipsMyOwnMessagesAndFollowsReadPosition() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		Member other = member("other");
		long room = startChat(me, peer);
		long otherRoom = startChat(me, other);
		getAs(me, CHATS + "/unread-count").andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));

		send(me, room, "내가 보냄");
		long first = send(peer, room, "하나");
		long second = send(peer, room, "둘");
		send(other, otherRoom, "셋");
		getAs(me, CHATS + "/unread-count").andExpect(jsonPath("$.count").value(3));
		getAs(peer, CHATS + "/unread-count").andExpect(jsonPath("$.count").value(0));

		patchAs(me, CHATS + "/" + room + "/read", Map.of("lastReadMessageId", first)).andExpect(status().isNoContent());
		getAs(me, CHATS + "/unread-count").andExpect(jsonPath("$.count").value(2));
		patchAs(me, CHATS + "/" + room + "/read", Map.of("lastReadMessageId", second)).andExpect(status().isNoContent());
		getAs(me, CHATS + "/unread-count").andExpect(jsonPath("$.count").value(1));
	}

	// 11.2 조회와 접근 제한

	@Test void nonParticipantCannotReadSendOrMarkRead() throws Exception {
		Member alice = member("alice");
		Member bob = member("bob");
		Member stranger = member("stranger");
		long room = startChat(alice, bob);
		long message = send(alice, room, "둘만의 대화");

		getAs(stranger, CHATS + "/" + room).andExpect(status().isNotFound());
		postAs(stranger, CHATS + "/" + room, Map.of("body", "끼어들기")).andExpect(status().isNotFound());
		postAs(stranger, CHATS + "/" + room, Map.of()).andExpect(status().isNotFound());
		patchAs(stranger, CHATS + "/" + room + "/read", Map.of("lastReadMessageId", message)).andExpect(status().isNotFound());
		getAs(stranger, CHATS + "/999999999").andExpect(status().isNotFound());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM messages m JOIN chat_members c ON c.id = m.chat_member_id WHERE c.room_id = ?", Integer.class, room));
	}

	@Test void projectRoomIsNotReachableThroughDirectApi() throws Exception {
		Member me = member("me");
		long room = rooms.save(ChatRoom.project(null)).getId();
		jdbc.update("INSERT INTO chat_members (room_id, user_id, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP)", room, me.getId());
		getAs(me, CHATS + "/" + room).andExpect(status().isNotFound());
		postAs(me, CHATS + "/" + room, Map.of("body", "안녕하세요")).andExpect(status().isNotFound());
	}

	@Test void detailReturnsPeerAndMessagesInAscendingOrder() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		getAs(me, CHATS + "/" + room).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(room))
			.andExpect(jsonPath("$.peer.tag").value(peer.getTag()))
			.andExpect(jsonPath("$.canSend").value(true))
			.andExpect(jsonPath("$.peerLastReadMessageId").isEmpty())
			.andExpect(jsonPath("$.messages.length()").value(0))
			.andExpect(jsonPath("$.nextBefore").isEmpty());

		long first = send(me, room, "첫째");
		long second = send(peer, room, "둘째");
		getAs(me, CHATS + "/" + room)
			.andExpect(jsonPath("$.messages[0].id").value(first))
			.andExpect(jsonPath("$.messages[0].body").value("첫째"))
			.andExpect(jsonPath("$.messages[0].mine").value(true))
			.andExpect(jsonPath("$.messages[1].id").value(second))
			.andExpect(jsonPath("$.messages[1].mine").value(false));
	}

	@Test void detailPagesWithBeforeAndAfter() throws Exception {
		Member me = member("me");
		long room = startChat(me, member("peer"));
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			ids.add(send(me, room, "메시지" + i));
		}
		String path = CHATS + "/" + room;

		getAs(me, path + "?size=2")
			.andExpect(jsonPath("$.messages.length()").value(2))
			.andExpect(jsonPath("$.messages[0].id").value(ids.get(3)))
			.andExpect(jsonPath("$.messages[1].id").value(ids.get(4)))
			.andExpect(jsonPath("$.nextBefore").value(ids.get(3)));
		getAs(me, path + "?size=2&before=" + ids.get(3))
			.andExpect(jsonPath("$.messages[0].id").value(ids.get(1)))
			.andExpect(jsonPath("$.messages[1].id").value(ids.get(2)))
			.andExpect(jsonPath("$.nextBefore").value(ids.get(1)));
		// 남은 수가 size와 같으면 더 불러올 것이 없다.
		getAs(me, path + "?size=1&before=" + ids.get(1))
			.andExpect(jsonPath("$.messages[0].id").value(ids.get(0)))
			.andExpect(jsonPath("$.nextBefore").isEmpty());
		getAs(me, path + "?after=" + ids.get(3))
			.andExpect(jsonPath("$.messages.length()").value(1))
			.andExpect(jsonPath("$.messages[0].id").value(ids.get(4)))
			.andExpect(jsonPath("$.nextBefore").isEmpty());
		getAs(me, path + "?size=2&after=" + ids.get(0))
			.andExpect(jsonPath("$.messages[0].id").value(ids.get(1)))
			.andExpect(jsonPath("$.messages[1].id").value(ids.get(2)));
		getAs(me, path + "?after=" + ids.get(4)).andExpect(jsonPath("$.messages.length()").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"before=1&after=1", "size=0", "size=101", "before=abc", "after=abc"})
	void detailRejectsInvalidParameters(String parameter) throws Exception {
		Member me = member("me");
		long room = startChat(me, member("peer"));
		getAs(me, CHATS + "/" + room + "?" + parameter).andExpect(status().isBadRequest());
	}

	// 11.3 전송

	@Test void sendTrimsAndChecksBodyLength() throws Exception {
		Member me = member("me");
		long room = startChat(me, member("peer"));
		String path = CHATS + "/" + room;

		postAs(me, path, Map.of("body", "  한 글자  ")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("한 글자")).andExpect(jsonPath("$.mine").value(true));
		postAs(me, path, Map.of("body", "a")).andExpect(status().isCreated());
		postAs(me, path, Map.of("body", "a".repeat(2000))).andExpect(status().isCreated());
		postAs(me, path, Map.of("body", " " + "a".repeat(2000) + " ")).andExpect(status().isCreated());

		postAs(me, path, Map.of("body", "")).andExpect(status().isBadRequest());
		postAs(me, path, Map.of("body", " \n\t ")).andExpect(status().isBadRequest());
		postAs(me, path, Map.of("body", "a".repeat(2001))).andExpect(status().isBadRequest());
		postAs(me, path, Map.of("body", "널\u0000문자")).andExpect(status().isBadRequest());
		postAs(me, path, Map.of()).andExpect(status().isBadRequest());
		postRawAs(me, path, "{\"body\":123}").andExpect(status().isBadRequest());
		postRawAs(me, path, "{깨진 JSON").andExpect(status().isBadRequest());
		assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM messages m JOIN chat_members c ON c.id = m.chat_member_id WHERE c.room_id = ?", Integer.class, room));
	}

	@Test void storesSeoulTimeAndRespondsWithOffset() throws Exception {
		Member me = member("me");
		long room = startChat(me, member("peer"));
		postAs(me, CHATS + "/" + room, Map.of("body", "시각 확인")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdAt").value("2026-11-06T10:15:00+09:00"));
		getAs(me, CHATS + "/" + room).andExpect(jsonPath("$.messages[0].createdAt").value("2026-11-06T10:15:00+09:00"));
		assertEquals("2026-11-06T10:15", jdbc.queryForObject("SELECT sent_at FROM messages m JOIN chat_members c ON c.id = m.chat_member_id WHERE c.room_id = ?", java.time.LocalDateTime.class, room).toString());
		assertEquals("2026-11-06T10:15", jdbc.queryForObject("SELECT joined_at FROM chat_members WHERE room_id = ? AND user_id = ?", java.time.LocalDateTime.class, room, me.getId()).toString());
	}

	// 시계가 멈춰 있어도 sent_at이 늘어나야 읽음 위치와 안 읽은 수가 어긋나지 않는다.
	@Test void sendsAtTheSameInstantKeepSentAtOrderAndUnreadCount() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		long first = send(peer, room, "하나");
		send(peer, room, "둘");
		send(peer, room, "셋");

		List<java.time.LocalDateTime> sentAt = jdbc.queryForList(
			"SELECT sent_at FROM messages m JOIN chat_members c ON c.id = m.chat_member_id WHERE c.room_id = ? ORDER BY m.id", java.time.LocalDateTime.class, room);
		assertTrue(sentAt.get(0).isBefore(sentAt.get(1)) && sentAt.get(1).isBefore(sentAt.get(2)), sentAt.toString());

		patchAs(me, CHATS + "/" + room + "/read", Map.of("lastReadMessageId", first)).andExpect(status().isNoContent());
		getAs(me, CHATS).andExpect(jsonPath("$.items[0].unreadCount").value(2));
	}

	@Test void suspendedPeerBlocksSendingButNotReading() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		send(me, room, "정지 전 메시지");
		suspend(peer);

		postAs(me, CHATS + "/" + room, Map.of("body", "정지 후 메시지")).andExpect(status().isConflict());
		getAs(me, CHATS + "/" + room).andExpect(status().isOk())
			.andExpect(jsonPath("$.canSend").value(false))
			.andExpect(jsonPath("$.messages.length()").value(1));
		getAs(me, CHATS).andExpect(jsonPath("$.items.length()").value(1));
	}

	// 11.6 읽음

	@Test void readPositionMovesForwardOnlyAndIsVisibleToPeer() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		long first = send(peer, room, "하나");
		long second = send(peer, room, "둘");
		String read = CHATS + "/" + room + "/read";

		getAs(peer, CHATS + "/" + room).andExpect(jsonPath("$.peerLastReadMessageId").isEmpty());
		patchAs(me, read, Map.of("lastReadMessageId", second)).andExpect(status().isNoContent()).andExpect(content().string(""));
		getAs(peer, CHATS + "/" + room).andExpect(jsonPath("$.peerLastReadMessageId").value(second));

		patchAs(me, read, Map.of("lastReadMessageId", first)).andExpect(status().isNoContent());
		getAs(peer, CHATS + "/" + room).andExpect(jsonPath("$.peerLastReadMessageId").value(second));
		getAs(me, CHATS).andExpect(jsonPath("$.items[0].unreadCount").value(0));
	}

	@Test void readRejectsMessagesOutsideTheRoom() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		send(peer, room, "이 방 메시지");
		long otherRoom = startChat(peer, member("other"));
		long otherMessage = send(peer, otherRoom, "다른 방 메시지");
		String read = CHATS + "/" + room + "/read";

		patchAs(me, read, Map.of("lastReadMessageId", otherMessage)).andExpect(status().isBadRequest());
		patchAs(me, read, Map.of("lastReadMessageId", Long.MAX_VALUE)).andExpect(status().isBadRequest());
		patchAs(me, read, new HashMap<>()).andExpect(status().isBadRequest());
		getAs(me, CHATS).andExpect(jsonPath("$.items[0].unreadCount").value(1));
	}

	@Test void sendingMovesMyReadPosition() throws Exception {
		Member me = member("me");
		Member peer = member("peer");
		long room = startChat(me, peer);
		send(peer, room, "먼저 온 메시지");
		getAs(me, CHATS).andExpect(jsonPath("$.items[0].unreadCount").value(1));

		long mine = send(me, room, "답장");
		getAs(me, CHATS).andExpect(jsonPath("$.items[0].unreadCount").value(0));
		getAs(peer, CHATS + "/" + room).andExpect(jsonPath("$.peerLastReadMessageId").value(mine));
	}
}
