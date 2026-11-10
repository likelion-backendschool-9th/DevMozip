package com.devmozip.backend.websocket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.devmozip.backend.config.MessengerRealtimeProperties;
import com.devmozip.backend.dto.message.TypingFrame;
import com.devmozip.backend.service.MessageService;
import com.devmozip.backend.service.ProjectChatService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 입력 중 신호를 방의 다른 참여자에게 전달한다. 저장하지 않고, Redis 장애 중에는 같은 서버 안에서만 전달한다.
 * 보낼 수 없는 방(참여하지 않음, 상대 정지, 종료·삭제된 프로젝트)의 신호는 연결을 닫지 않고 무시한다.
 */
@Component
public class MessengerTypingRelay {
	static final String DIRECT = "direct";
	static final String PROJECT = "project";
	private static final String ACCESS_CACHE = "messenger.typingAccess";
	// 오래된 간격 기록을 지우는 주기(받은 신호 수)
	private static final int PURGE_EVERY = 1000;

	private final MessageService directChats;
	private final ProjectChatService projectChats;
	private final MessengerSignalDispatcher dispatcher;
	private final MessengerRealtimeProperties properties;
	private final Clock clock;
	private final Map<String, Instant> lastAccepted = new ConcurrentHashMap<>();
	private final AtomicInteger acceptedSincePurge = new AtomicInteger();

	public MessengerTypingRelay(MessageService directChats, ProjectChatService projectChats, MessengerSignalDispatcher dispatcher,
		MessengerRealtimeProperties properties, @Qualifier("messengerClock") Clock clock) {
		this.directChats = directChats;
		this.projectChats = projectChats;
		this.dispatcher = dispatcher;
		this.properties = properties;
		this.clock = clock;
	}

	private record Target(List<Long> recipients, TypingFrame frame) {
	}

	private record Cached(Optional<Target> target, Instant expiresAt) {
	}

	static boolean supportsRoomType(String roomType) {
		return DIRECT.equals(roomType) || PROJECT.equals(roomType);
	}

	void relay(WebSocketSession session, long memberId, String roomType, long roomId) {
		Instant now = clock.instant();
		if (!acquire(memberId + ":" + roomType + ":" + roomId, now)) {
			return;
		}
		target(session, memberId, roomType, roomId, now)
			.filter(target -> !target.recipients().isEmpty())
			.ifPresent(target -> dispatcher.dispatch(target.recipients(), target.frame()));
	}

	// 회원·방당 간격 안의 신호는 무시한다. 같은 회원의 여러 탭을 함께 센다.
	private boolean acquire(String key, Instant now) {
		Duration interval = properties.getTypingInterval();
		AtomicBoolean accepted = new AtomicBoolean();
		lastAccepted.compute(key, (ignored, last) -> {
			if (last != null && now.isBefore(last.plus(interval))) {
				return last;
			}
			accepted.set(true);
			return now;
		});
		if (accepted.get() && acceptedSincePurge.incrementAndGet() >= PURGE_EVERY) {
			acceptedSincePurge.set(0);
			Instant expired = now.minus(interval);
			lastAccepted.values().removeIf(last -> last.isBefore(expired));
		}
		return accepted.get();
	}

	// 참여 여부를 연결마다 잠시 기억해, 입력할 때마다 DB를 읽지 않는다.
	@SuppressWarnings("unchecked")
	private Optional<Target> target(WebSocketSession session, long memberId, String roomType, long roomId, Instant now) {
		Map<String, Cached> cache = (Map<String, Cached>) session.getAttributes()
			.computeIfAbsent(ACCESS_CACHE, ignored -> new ConcurrentHashMap<String, Cached>());
		String key = roomType + ":" + roomId;
		Cached cached = cache.get(key);
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return cached.target();
		}
		Optional<Target> target = lookup(memberId, roomType, roomId);
		cache.put(key, new Cached(target, now.plus(properties.getTypingAccessCache())));
		return target;
	}

	private Optional<Target> lookup(long memberId, String roomType, long roomId) {
		if (DIRECT.equals(roomType)) {
			return directChats.directTypingRecipients(memberId, roomId)
				.map(recipients -> new Target(recipients, TypingFrame.direct(roomId)));
		}
		return projectChats.typingAccess(memberId, roomId)
			.map(access -> new Target(access.recipients(), TypingFrame.project(roomId, access.nickname())));
	}

	int trackedIntervals() {
		return lastAccepted.size();
	}
}
