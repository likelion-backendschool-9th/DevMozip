package com.devmozip.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.message.ChatDetailResponse;
import com.devmozip.backend.dto.message.ChatListItemResponse;
import com.devmozip.backend.dto.message.ChatListResponse;
import com.devmozip.backend.dto.message.ChatMessageResponse;
import com.devmozip.backend.dto.message.ChatPeerResponse;
import com.devmozip.backend.dto.message.MessageCreatedFrame;
import com.devmozip.backend.dto.message.MessageReadFrame;
import com.devmozip.backend.dto.message.StartChatResponse;
import com.devmozip.backend.dto.message.UnreadCountResponse;
import com.devmozip.backend.entity.ChatMember;
import com.devmozip.backend.entity.ChatRoom;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.entity.Message;
import com.devmozip.backend.repository.ChatMemberRepository;
import com.devmozip.backend.repository.ChatRoomRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.MessageRepository;
import com.devmozip.backend.websocket.MessengerSignal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.devmozip.backend.service.MessengerRules.badRequest;
import static com.devmozip.backend.service.MessengerRules.notFound;
import static com.devmozip.backend.service.MessengerRules.pageSize;
import static com.devmozip.backend.service.MessengerRules.validBody;

/** 1:1 메시지의 정책과 트랜잭션. 회원은 ID로 받아 WebSocket 처리에서도 같은 서비스를 쓸 수 있게 한다. */
@Service
@Transactional(readOnly = true)
public class MessageService {
	private static final String DIRECT_TYPE = "direct";
	private static final int DEFAULT_LIST_SIZE = 30;
	private static final int DEFAULT_MESSAGE_SIZE = 50;
	private static final String ROOM_NOT_FOUND = "대화방을 찾을 수 없습니다.";
	private static final String TARGET_NOT_FOUND = "대화 상대를 찾을 수 없습니다.";

	private final MemberRepository members;
	private final ChatRoomRepository rooms;
	private final ChatMemberRepository chatMembers;
	private final MessageRepository messages;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public MessageService(MemberRepository members, ChatRoomRepository rooms, ChatMemberRepository chatMembers,
		MessageRepository messages, @Qualifier("messengerClock") Clock clock, ApplicationEventPublisher events) {
		this.members = members;
		this.rooms = rooms;
		this.chatMembers = chatMembers;
		this.messages = messages;
		this.clock = clock;
		this.events = events;
	}

	public long currentMemberId(String email) {
		return members.findByEmail(email)
			.map(Member::getId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 회원을 찾을 수 없습니다."));
	}

	/** 두 회원 쌍당 방 하나만 둔다. 이미 있으면 기존 방을 돌려준다. */
	@Transactional
	public StartChatResponse startDirectChat(long memberId, String targetSlug) {
		if (targetSlug == null || targetSlug.isBlank()) {
			throw badRequest("대화 상대를 확인해 주세요.");
		}
		// PostgreSQL 문자열에는 NUL을 넣을 수 없어 조회하면 500이 된다. 그런 태그는 없으므로 404로 응답한다.
		if (targetSlug.indexOf('\0') >= 0) {
			throw notFound(TARGET_NOT_FOUND);
		}
		Member target = members.findByTagAndStatus(targetSlug, MemberStatus.ACTIVE)
			.orElseThrow(() -> notFound(TARGET_NOT_FOUND));
		if (target.getId() == memberId) {
			throw badRequest("본인과는 대화를 시작할 수 없습니다.");
		}

		// 두 회원을 항상 ID 순서로 잠가 교착 없이 동시 요청을 한 줄로 세운다. 잠금 뒤에 조회해야 먼저 만든 방이 보인다.
		Member first = lockMember(Math.min(memberId, target.getId()));
		Member second = lockMember(Math.max(memberId, target.getId()));
		List<Long> existing = rooms.findDirectRoomIds(first.getId(), second.getId());
		if (!existing.isEmpty()) {
			return new StartChatResponse(existing.getFirst());
		}

		LocalDateTime now = MessengerRules.now(clock);
		ChatRoom room = rooms.save(ChatRoom.direct());
		chatMembers.save(new ChatMember(room, first, now));
		chatMembers.save(new ChatMember(room, second, now));
		return new StartChatResponse(room.getId());
	}

	public ChatListResponse listChats(long memberId, String type, String query, Long cursor, Integer size) {
		if (!DIRECT_TYPE.equals(type)) {
			throw badRequest("지원하지 않는 채팅방 유형입니다.");
		}
		int pageSize = pageSize(size, DEFAULT_LIST_SIZE);
		List<Long> lastMessageIds = messages.findLastMessageIds(memberId, MessengerRules.containsPattern(query),
			cursor == null ? Long.MAX_VALUE : cursor, Limit.of(pageSize + 1));
		boolean hasNext = lastMessageIds.size() > pageSize;
		List<Long> pageIds = hasNext ? lastMessageIds.subList(0, pageSize) : lastMessageIds;
		if (pageIds.isEmpty()) {
			return new ChatListResponse(List.of(), null);
		}

		Map<Long, Message> lastMessages = messages.findWithRoomByIdIn(pageIds).stream()
			.collect(Collectors.toMap(Message::getId, Function.identity()));
		List<Long> roomIds = pageIds.stream().map(id -> roomIdOf(lastMessages.get(id))).toList();
		Map<Long, Member> peers = chatMembers.findPeers(roomIds, memberId).stream()
			.collect(Collectors.toMap(peer -> peer.getRoom().getId(), ChatMember::getMember));
		Map<Long, Long> unreadCounts = messages.countUnreadByRoom(memberId, roomIds).stream()
			.collect(Collectors.toMap(MessageRepository.RoomUnreadCount::getRoomId, MessageRepository.RoomUnreadCount::getUnreadCount));

		List<ChatListItemResponse> items = pageIds.stream().map(id -> {
			Message last = lastMessages.get(id);
			long roomId = roomIdOf(last);
			return new ChatListItemResponse(
				roomId,
				ChatPeerResponse.from(peers.get(roomId)),
				ChatMessageResponse.from(last, last.getChatMember().getMember().getId() == memberId),
				unreadCounts.getOrDefault(roomId, 0L));
		}).toList();
		return new ChatListResponse(items, hasNext ? pageIds.getLast() : null);
	}

	public UnreadCountResponse countUnread(long memberId) {
		return new UnreadCountResponse(messages.countUnreadDirect(memberId));
	}

	/** 파라미터가 없으면 최신 메시지, before는 이전 기록, after는 새 메시지. 결과는 항상 오름차순이다. */
	public ChatDetailResponse getDirectChat(long memberId, long roomId, Long before, Long after, Integer size) {
		MessengerRules.rejectBeforeAndAfter(before, after);
		int pageSize = pageSize(size, DEFAULT_MESSAGE_SIZE);
		ChatMember mine = participant(roomId, memberId);
		ChatMember peer = peerOf(roomId, memberId);
		MessengerRules.MessagePage page = MessengerRules.messagePage(messages, roomId, before, after, pageSize);

		Long peerLastReadMessageId = peer.getLastReadAt() == null
			? null
			: messages.findLastIdSentUntil(roomId, peer.getLastReadAt());
		return new ChatDetailResponse(
			roomId,
			ChatPeerResponse.from(peer.getMember()),
			canSendTo(peer),
			peerLastReadMessageId,
			page.messages().stream().map(message -> ChatMessageResponse.from(message, message.getChatMember().getId().equals(mine.getId()))).toList(),
			page.nextBefore());
	}

	@Transactional
	public ChatMessageResponse sendDirectMessage(long memberId, long roomId, String body) {
		ChatMember mine = participant(roomId, memberId);
		String content = validBody(body);
		ChatMember peer = peerOf(roomId, memberId);
		if (!canSendTo(peer)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "상대가 이용 정지 상태라 메시지를 보낼 수 없습니다.");
		}

		rooms.findForSendById(roomId).orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		LocalDateTime sentAt = MessengerRules.nextSentAt(messages, clock, roomId);
		Message message = messages.save(new Message(mine, content, sentAt));
		chatMembers.moveReadPosition(mine.getId(), sentAt);
		// 보낸 사람의 읽음 위치도 옮겨지지만, 상대가 after로 조회하면 함께 받으므로 읽음 신호를 따로 보내지 않는다.
		events.publishEvent(new MessengerSignal(List.of(memberId, peer.getMember().getId()), MessageCreatedFrame.direct(roomId, message.getId())));
		return ChatMessageResponse.from(message, true);
	}

	/** 그 메시지의 sent_at을 읽음 위치로 저장한다. 더 이전 메시지면 위치를 바꾸지 않는다. */
	@Transactional
	public void markDirectChatRead(long memberId, long roomId, Long lastReadMessageId) {
		ChatMember mine = participant(roomId, memberId);
		if (lastReadMessageId == null) {
			throw badRequest("읽은 메시지를 확인해 주세요.");
		}
		Message message = messages.findInRoom(lastReadMessageId, roomId)
			.orElseThrow(() -> badRequest("이 대화의 메시지가 아닙니다."));
		// 화면이 같은 위치를 반복해서 보내도 위치가 실제로 옮겨졌을 때만 신호를 보낸다.
		if (chatMembers.moveReadPosition(mine.getId(), message.getSentAt()) > 0) {
			long peerId = peerOf(roomId, memberId).getMember().getId();
			events.publishEvent(new MessengerSignal(List.of(memberId, peerId), MessageReadFrame.direct(roomId, message.getId())));
		}
	}

	/**
	 * 입력 중 신호를 받을 회원. 참여하지 않은 방이거나 상대가 정지돼 보낼 수 없으면 비어 있다.
	 * WebSocket 프레임 처리에서 부르므로 예외 대신 빈 값으로 알린다.
	 */
	public Optional<List<Long>> directTypingRecipients(long memberId, long roomId) {
		if (chatMembers.findDirectParticipant(roomId, memberId).isEmpty()) {
			return Optional.empty();
		}
		return chatMembers.findPeers(List.of(roomId), memberId).stream().findFirst()
			.filter(MessageService::canSendTo)
			.map(peer -> List.of(peer.getMember().getId()));
	}

	private Member lockMember(long id) {
		return members.findForMessengerById(id).orElseThrow(() -> notFound(TARGET_NOT_FOUND));
	}

	// 참여자가 아니면 방의 존재를 드러내지 않도록 없는 방과 같은 404로 응답한다.
	private ChatMember participant(long roomId, long memberId) {
		return chatMembers.findDirectParticipant(roomId, memberId).orElseThrow(() -> notFound(ROOM_NOT_FOUND));
	}

	private ChatMember peerOf(long roomId, long memberId) {
		return chatMembers.findPeers(List.of(roomId), memberId).stream().findFirst()
			.orElseThrow(() -> notFound(ROOM_NOT_FOUND));
	}

	private static boolean canSendTo(ChatMember peer) {
		return peer.getMember().getStatus() != MemberStatus.SUSPENDED;
	}

	private static long roomIdOf(Message message) {
		return message.getChatMember().getRoom().getId();
	}
}
