package com.devmozip.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.message.MessageCreatedFrame;
import com.devmozip.backend.dto.message.MessageReadFrame;
import com.devmozip.backend.dto.message.ProjectChatDetailResponse;
import com.devmozip.backend.dto.message.ProjectChatListItemResponse;
import com.devmozip.backend.dto.message.ProjectChatListResponse;
import com.devmozip.backend.dto.message.ProjectChatMemberResponse;
import com.devmozip.backend.dto.message.ProjectChatMessageResponse;
import com.devmozip.backend.dto.message.UnreadCountResponse;
import com.devmozip.backend.entity.ChatMember;
import com.devmozip.backend.entity.ChatRoom;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Message;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.repository.ChatMemberRepository;
import com.devmozip.backend.repository.ChatRoomRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.MessageRepository;
import com.devmozip.backend.repository.ProjectChatAccessRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.websocket.MessengerSignal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.devmozip.backend.service.MessengerRules.badRequest;
import static com.devmozip.backend.service.MessengerRules.conflict;
import static com.devmozip.backend.service.MessengerRules.forbidden;
import static com.devmozip.backend.service.MessengerRules.notFound;
import static com.devmozip.backend.service.MessengerRules.pageSize;
import static com.devmozip.backend.service.MessengerRules.validBody;

/**
 * 프로젝트 단톡방(7.18~7.23). 프로젝트 코드를 고치지 않도록 project_members를 읽기만 한다.
 * 승인·취소는 다음 접근 때 chat_members에 반영한다. 경로와 신호의 ID는 모두 프로젝트 ID다.
 */
@Service
@Transactional(readOnly = true)
public class ProjectChatService {
	private static final int DEFAULT_LIST_SIZE = 30;
	private static final int DEFAULT_MESSAGE_SIZE = 50;
	private static final String ROOM_NOT_FOUND = "프로젝트 채팅을 찾을 수 없습니다.";

	private final MemberRepository members;
	private final ProjectRepository projects;
	private final ProjectChatAccessRepository projectMembers;
	private final ChatRoomRepository rooms;
	private final ChatMemberRepository chatMembers;
	private final MessageRepository messages;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public ProjectChatService(MemberRepository members, ProjectRepository projects, ProjectChatAccessRepository projectMembers,
		ChatRoomRepository rooms, ChatMemberRepository chatMembers, MessageRepository messages,
		@Qualifier("messengerClock") Clock clock, ApplicationEventPublisher events) {
		this.members = members;
		this.projects = projects;
		this.projectMembers = projectMembers;
		this.rooms = rooms;
		this.chatMembers = chatMembers;
		this.messages = messages;
		this.clock = clock;
		this.events = events;
	}

	/** 입력 중 신호를 받을 회원과 표시할 닉네임 */
	public record TypingAccess(List<Long> recipients, String nickname) {
	}

	/** 입장 확인과 참여 행 반영을 마친 방 */
	private record Entered(Project project, ChatRoom room, ChatMember mine, List<ChatMember> chatMembers,
		List<ProjectMember> projectMembers) {
	}

	public long currentMemberId(String email) {
		return members.findByEmail(email)
			.map(Member::getId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 회원을 찾을 수 없습니다."));
	}

	/** 입장할 수 있는 방을 프로젝트 ID 역순으로 돌려준다. 방 행이 아직 없는 프로젝트도 포함한다. */
	public ProjectChatListResponse listRooms(long memberId, String query, Long cursor, Integer size) {
		int pageSize = pageSize(size, DEFAULT_LIST_SIZE);
		List<Long> ids = projectMembers.findEnterableProjectIds(memberId, today(), cursor == null ? Long.MAX_VALUE : cursor,
			MessengerRules.containsPattern(query), Limit.of(pageSize + 1));
		boolean hasNext = ids.size() > pageSize;
		List<Long> pageIds = hasNext ? ids.subList(0, pageSize) : ids;
		if (pageIds.isEmpty()) {
			return new ProjectChatListResponse(List.of(), null);
		}

		Map<Long, Project> byId = projects.findAllById(pageIds).stream()
			.collect(Collectors.toMap(Project::getId, Function.identity()));
		Map<Long, Long> memberCounts = projectMembers.countChatMembers(pageIds).stream()
			.collect(Collectors.toMap(ProjectChatAccessRepository.ProjectMemberCount::getProjectId,
				ProjectChatAccessRepository.ProjectMemberCount::getMemberCount));
		Map<Long, Long> unreadCounts = messages.countUnreadByProject(memberId, pageIds).stream()
			.collect(Collectors.toMap(MessageRepository.ProjectUnreadCount::getProjectId,
				MessageRepository.ProjectUnreadCount::getUnreadCount));
		List<ProjectChatListItemResponse> items = pageIds.stream().map(id -> {
			Project project = byId.get(id);
			return new ProjectChatListItemResponse(id, project.getTitle(), project.getStatus().name(),
				memberCounts.getOrDefault(id, 0L), unreadCounts.getOrDefault(id, 0L));
		}).toList();
		return new ProjectChatListResponse(items, hasNext ? pageIds.getLast() : null);
	}

	public UnreadCountResponse countUnread(long memberId) {
		return new UnreadCountResponse(messages.countUnreadProject(memberId));
	}

	/** 첫 접근이면 방을 만들고, 승인·취소를 참여 행에 반영한 뒤 조회한다. */
	@Transactional
	public ProjectChatDetailResponse getProjectChat(long memberId, long projectId, Long before, Long after, Integer size) {
		MessengerRules.rejectBeforeAndAfter(before, after);
		int pageSize = pageSize(size, DEFAULT_MESSAGE_SIZE);
		Entered entered = enter(memberId, projectId, false);
		long roomId = entered.room().getId();
		MessengerRules.MessagePage page = MessengerRules.messagePage(messages, roomId, before, after, pageSize);

		Map<Long, ChatMember> senders = entered.chatMembers().stream()
			.collect(Collectors.toMap(ChatMember::getId, Function.identity()));
		Map<Long, String> roles = entered.projectMembers().stream()
			.collect(Collectors.toMap(ProjectMember::getUserId, member -> member.getRole().name()));
		Map<Long, Long> readPositions = new HashMap<>();
		messages.findReadPositions(roomId).forEach(position ->
			readPositions.put(position.getChatMemberId(), position.getLastReadMessageId()));

		long mineId = entered.mine().getId();
		List<ProjectChatMemberResponse> memberResponses = entered.chatMembers().stream()
			.map(chatMember -> ProjectChatMemberResponse.from(chatMember, chatMember.getId() == mineId,
				roles.get(chatMember.getMember().getId()), readPositions.get(chatMember.getId())))
			.toList();
		List<ProjectChatMessageResponse> messageResponses = page.messages().stream().map(message -> {
			ChatMember sender = senders.get(message.getChatMember().getId());
			return ProjectChatMessageResponse.from(message, sender, sender.getId() == mineId);
		}).toList();
		Project project = entered.project();
		return new ProjectChatDetailResponse(projectId, project.getTitle(), project.getStatus().name(),
			canSend(project), project.getHostMemberId() == memberId, memberResponses, messageResponses, page.nextBefore());
	}

	@Transactional
	public ProjectChatMessageResponse sendProjectMessage(long memberId, long projectId, String body) {
		Entered entered = enter(memberId, projectId, true);
		String content = validBody(body);
		if (!canSend(entered.project())) {
			throw conflict("종료된 프로젝트 채팅에는 메시지를 보낼 수 없습니다.");
		}
		ChatMember mine = entered.mine();
		long roomId = entered.room().getId();
		LocalDateTime sentAt = MessengerRules.nextSentAt(messages, clock, roomId);
		Message message = messages.save(new Message(mine, content, sentAt));
		chatMembers.moveReadPosition(mine.getId(), sentAt);
		events.publishEvent(new MessengerSignal(currentMemberIds(entered.chatMembers()),
			MessageCreatedFrame.project(projectId, message.getId())));
		return ProjectChatMessageResponse.from(message, mine, true);
	}

	/** 그 메시지의 sent_at을 읽음 위치로 저장한다. 더 이전 메시지면 위치를 바꾸지 않는다. */
	@Transactional
	public void markProjectChatRead(long memberId, long projectId, Long lastReadMessageId) {
		Project project = liveProject(projectId);
		requireEnterable(project, memberId);
		ChatRoom room = rooms.findProjectRoom(projectId).filter(found -> !found.isDeleted())
			.orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		ChatMember mine = chatMembers.findInRoom(room.getId(), memberId).filter(found -> !found.hasLeft())
			.orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		if (lastReadMessageId == null) {
			throw badRequest("읽은 메시지를 확인해 주세요.");
		}
		Message message = messages.findInRoom(lastReadMessageId, room.getId())
			.orElseThrow(() -> badRequest("이 대화의 메시지가 아닙니다."));
		if (chatMembers.moveReadPosition(mine.getId(), message.getSentAt()) > 0) {
			events.publishEvent(new MessengerSignal(currentMemberIds(chatMembers.findAllInRoom(room.getId())),
				MessageReadFrame.project(projectId, message.getId(), mine.getMember().getTag())));
		}
	}

	/** 논리 삭제. 종료된 프로젝트의 주최자만 할 수 있다. 방이 없으면 삭제된 방 행을 만들어 다시 생기지 않게 한다. */
	@Transactional
	public void deleteProjectChat(long memberId, long projectId) {
		// 방이 없을 때 동시에 첫 접근이 방을 만들지 않도록 프로젝트를 먼저 잠근다. 잠금 순서는 항상 프로젝트, 방이다.
		Project project = projects.findLiveForUpdate(projectId).orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		if (project.getHostMemberId() != memberId) {
			boolean member = activeMembership(projectId, memberId).isPresent();
			throw member ? forbidden("주최자만 프로젝트 채팅방을 삭제할 수 있습니다.") : notFound(ROOM_NOT_FOUND);
		}
		if (project.getStatus() != ProjectStatus.COMPLETED) {
			throw conflict("프로젝트가 종료된 뒤에 채팅방을 삭제할 수 있습니다.");
		}
		LocalDateTime now = MessengerRules.now(clock);
		rooms.findProjectRoomForUpdate(projectId).ifPresentOrElse(
			room -> room.delete(now),
			() -> rooms.save(ChatRoom.deletedProject(project, now)));
	}

	/**
	 * 강퇴 가결 시 #49가 부른다. 이후 프로젝트 참여 이력과 무관하게 입장할 수 없다.
	 * 방이나 참여 행이 없으면 만들어 두어 다음 접근 때 참여 행이 생기지 않게 한다.
	 */
	@Transactional
	public void excludeMember(long projectId, long memberId) {
		Project project = projects.findLiveForUpdate(projectId).orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		Member member = members.findById(memberId).orElseThrow(() -> notFound("회원을 찾을 수 없습니다."));
		LocalDateTime now = MessengerRules.now(clock);
		ChatRoom room = rooms.findProjectRoomForUpdate(projectId).orElseGet(() -> rooms.save(ChatRoom.project(project)));
		chatMembers.findInRoom(room.getId(), memberId).ifPresentOrElse(
			chatMember -> chatMember.leave(now),
			() -> chatMembers.save(ChatMember.excluded(room, member, now)));
	}

	/** 입력 중 신호를 보낼 수 있으면 받을 회원을 돌려준다. WebSocket 프레임 처리에서 부르므로 예외 대신 빈 값으로 알린다. */
	public Optional<TypingAccess> typingAccess(long memberId, long projectId) {
		Project project = projects.findByIdAndDeletedAtIsNull(projectId).orElse(null);
		if (project == null || !canSend(project) || !started(project) || activeMembership(projectId, memberId).isEmpty()) {
			return Optional.empty();
		}
		ChatRoom room = rooms.findProjectRoom(projectId).filter(found -> !found.isDeleted()).orElse(null);
		if (room == null) {
			return Optional.empty();
		}
		List<ChatMember> current = chatMembers.findAllInRoom(room.getId());
		ChatMember mine = current.stream().filter(chatMember -> chatMember.getMember().getId() == memberId)
			.findFirst().filter(chatMember -> !chatMember.hasLeft()).orElse(null);
		if (mine == null) {
			return Optional.empty();
		}
		Set<Long> active = activeMemberIds(projectMembers.findByProject(projectId));
		List<Long> recipients = current.stream()
			.filter(chatMember -> !chatMember.hasLeft())
			.map(chatMember -> chatMember.getMember().getId())
			.filter(id -> id != memberId && active.contains(id))
			.toList();
		return Optional.of(new TypingAccess(recipients, mine.getMember().getNickname()));
	}

	/**
	 * 조회와 전송의 공통 입장 확인. 방이 없으면 만들고, project_members와 다른 참여 행을 고친다.
	 * 전송은 방을 먼저 잠가 메시지 순서와 받는 사람을 고정한다.
	 */
	private Entered enter(long memberId, long projectId, boolean forSend) {
		Project project = liveProject(projectId);
		requireEnterable(project, memberId);
		ChatRoom room = (forSend ? rooms.findProjectRoomForUpdate(projectId) : rooms.findProjectRoom(projectId))
			.orElseGet(() -> createRoom(projectId));
		if (room.isDeleted()) {
			throw notFound(ROOM_NOT_FOUND);
		}
		List<ProjectMember> currentProjectMembers = projectMembers.findByProject(projectId);
		List<ChatMember> current = syncChatMembers(room, projectId, activeMemberIds(currentProjectMembers), forSend);
		ChatMember mine = current.stream().filter(chatMember -> chatMember.getMember().getId() == memberId)
			.findFirst().filter(chatMember -> !chatMember.hasLeft())
			.orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		return new Entered(project, room, mine, current, currentProjectMembers);
	}

	// 동시 첫 접근이 방을 두 개 만들지 않도록 프로젝트를 잠근 뒤 다시 확인한다. 방이 생긴 뒤에는 이 잠금을 쓰지 않는다.
	private ChatRoom createRoom(long projectId) {
		Project locked = projects.findLiveForUpdate(projectId).orElseThrow(() -> notFound(ROOM_NOT_FOUND));
		return rooms.findProjectRoomForUpdate(projectId).orElseGet(() -> rooms.save(ChatRoom.project(locked)));
	}

	/**
	 * 승인된 회원의 참여 행을 만들고, ACTIVE가 아닌 회원의 행에 퇴장 시각을 채운다.
	 * 바꿀 것이 없으면 잠그지 않는다. 바꿀 때는 방을 잠근 뒤 다시 읽어 동시 접근이 같은 행을 두 번 만들지 않게 한다.
	 */
	private List<ChatMember> syncChatMembers(ChatRoom room, long projectId, Set<Long> active, boolean roomLocked) {
		long roomId = room.getId();
		boolean changed = projectMembers.countMissingChatMembers(projectId, roomId) > 0
			|| projectMembers.countDepartedChatMembers(projectId, roomId) > 0;
		if (!changed) {
			return chatMembers.findAllInRoom(roomId);
		}
		if (!roomLocked) {
			rooms.findForSendById(roomId);
		}
		LocalDateTime now = MessengerRules.now(clock);
		List<ChatMember> current = new ArrayList<>(chatMembers.findAllInRoom(roomId));
		Set<Long> present = new HashSet<>();
		for (ChatMember chatMember : current) {
			present.add(chatMember.getMember().getId());
			if (!active.contains(chatMember.getMember().getId())) {
				chatMember.leave(now);
			}
		}
		List<Long> missing = active.stream().filter(id -> !present.contains(id)).toList();
		// 같은 시각에 들어온 회원은 회원 ID 순으로 둔다. 조회 결과의 순서는 보장되지 않는다.
		List<Member> joining = members.findAllById(missing).stream().sorted(Comparator.comparing(Member::getId)).toList();
		for (Member member : joining) {
			current.add(chatMembers.save(new ChatMember(room, member, now)));
		}
		return current;
	}

	private Project liveProject(long projectId) {
		return projects.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> notFound(ROOM_NOT_FOUND));
	}

	// 멤버가 아니거나 모집 시작 전이면 방의 존재를 드러내지 않도록 404로 응답한다.
	private void requireEnterable(Project project, long memberId) {
		if (activeMembership(project.getId(), memberId).isEmpty() || !started(project)) {
			throw notFound(ROOM_NOT_FOUND);
		}
	}

	private Optional<ProjectMember> activeMembership(long projectId, long memberId) {
		return projectMembers.findMembership(projectId, memberId)
			.filter(membership -> membership.getStatus() == ProjectMember.Status.ACTIVE);
	}

	private boolean started(Project project) {
		return !today().isBefore(project.getRecruitmentStart());
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private static boolean canSend(Project project) {
		return project.getStatus() != ProjectStatus.COMPLETED;
	}

	private static Set<Long> activeMemberIds(List<ProjectMember> projectMembers) {
		return projectMembers.stream()
			.filter(member -> member.getStatus() == ProjectMember.Status.ACTIVE)
			.map(ProjectMember::getUserId)
			.collect(Collectors.toSet());
	}

	// 신호를 받을 회원. 나간 회원은 받지 않는다.
	private static List<Long> currentMemberIds(List<ChatMember> chatMembers) {
		return chatMembers.stream()
			.filter(chatMember -> !chatMember.hasLeft())
			.map(chatMember -> chatMember.getMember().getId())
			.toList();
	}
}
