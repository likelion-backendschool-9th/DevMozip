package com.devmozip.backend.message;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.ChatMember;
import com.devmozip.backend.entity.ChatRoom;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Message;
import com.devmozip.backend.repository.ChatMemberRepository;
import com.devmozip.backend.repository.ChatRoomRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.MessageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL과 local(H2) 프로필이 같은 방식으로 저장하고 읽는지 확인한다. */
abstract class ChatEntityRoundTripTests {
	@Autowired MemberRepository members;
	@Autowired ChatRoomRepository rooms;
	@Autowired ChatMemberRepository chatMembers;
	@Autowired MessageRepository messages;
	@Autowired EntityManager entityManager;

	@Test @Transactional void savesRoomParticipantAndMessage() {
		LocalDateTime now = LocalDateTime.of(2026, 11, 6, 10, 15, 0, 123_456_000);
		Member sender = members.save(Member.builder().email("round-trip@devmozip.test").nickname("왕복회원").build());
		ChatRoom room = rooms.save(ChatRoom.direct());
		ChatMember chatMember = chatMembers.save(new ChatMember(room, sender, now));
		Message message = messages.save(new Message(chatMember, "안녕하세요", now));
		entityManager.flush();
		entityManager.clear();

		Message found = messages.findById(message.getId()).orElseThrow();
		assertEquals("안녕하세요", found.getContent());
		assertEquals(now, found.getSentAt());
		assertNull(found.getDeletedAt());
		ChatMember foundMember = found.getChatMember();
		assertEquals(sender.getId(), foundMember.getMember().getId());
		assertEquals(now, foundMember.getJoinedAt());
		assertNull(foundMember.getLastReadAt());
		assertNull(foundMember.getLeftAt());
		assertEquals(room.getId(), foundMember.getRoom().getId());
		assertEquals(ChatRoom.Type.DIRECT, foundMember.getRoom().getType());
		assertNull(foundMember.getRoom().getProject());
		assertNull(foundMember.getRoom().getDeletedAt());
	}

	// project_id의 UNIQUE가 프로젝트가 없는 1:1 방끼리 충돌하면 안 된다.
	@Test @Transactional void savesManyDirectRoomsWithoutProject() {
		rooms.save(ChatRoom.direct());
		rooms.save(ChatRoom.direct());
		assertDoesNotThrow(entityManager::flush);
	}
}
