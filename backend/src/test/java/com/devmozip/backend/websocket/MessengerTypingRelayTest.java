package com.devmozip.backend.websocket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.devmozip.backend.config.MessengerRealtimeProperties;
import com.devmozip.backend.dto.message.TypingFrame;
import com.devmozip.backend.service.MessageService;
import com.devmozip.backend.service.ProjectChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MessengerTypingRelayTest {
	private static final long ALICE = 1;
	private static final long BOB = 2;
	private static final long ROOM = 12;

	private final MessageService directChats = mock(MessageService.class);
	private final ProjectChatService projectChats = mock(ProjectChatService.class);
	private final MessengerSignalDispatcher dispatcher = mock(MessengerSignalDispatcher.class);
	private final MessengerRealtimeProperties properties = new MessengerRealtimeProperties();
	private final MutableClock clock = new MutableClock();
	private MessengerTypingRelay relay;

	/** 테스트가 직접 움직이는 시계 */
	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-11-08T01:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override public Instant instant() {
			return now;
		}
	}

	@BeforeEach void setUp() {
		properties.setTypingInterval(Duration.ofSeconds(1));
		properties.setTypingAccessCache(Duration.ofSeconds(10));
		relay = new MessengerTypingRelay(directChats, projectChats, dispatcher, properties, clock);
		when(directChats.directTypingRecipients(anyLong(), anyLong())).thenReturn(Optional.of(List.of(BOB)));
	}

	private static WebSocketSession session() {
		WebSocketSession session = mock(WebSocketSession.class);
		Map<String, Object> attributes = new ConcurrentHashMap<>();
		when(session.getAttributes()).thenReturn(attributes);
		return session;
	}

	@Test void deliversToRecipientsWithTheDirectFrame() {
		relay.relay(session(), ALICE, "direct", ROOM);
		verify(dispatcher).dispatch(List.of(BOB), TypingFrame.direct(ROOM));
	}

	@Test void dropsFramesWithinTheIntervalPerMemberAndRoomAcrossTabs() {
		WebSocketSession firstTab = session();
		WebSocketSession secondTab = session();
		relay.relay(firstTab, ALICE, "direct", ROOM);
		clock.advance(Duration.ofMillis(999));
		relay.relay(secondTab, ALICE, "direct", ROOM);
		verify(dispatcher, times(1)).dispatch(any(), any());

		clock.advance(Duration.ofMillis(1));
		relay.relay(secondTab, ALICE, "direct", ROOM);
		verify(dispatcher, times(2)).dispatch(any(), any());
	}

	@Test void otherRoomsAndMembersAreNotLimitedTogether() {
		relay.relay(session(), ALICE, "direct", ROOM);
		relay.relay(session(), ALICE, "direct", ROOM + 1);
		relay.relay(session(), BOB, "direct", ROOM);
		relay.relay(session(), ALICE, "project", ROOM);
		verify(dispatcher, times(3)).dispatch(any(), any());
		verify(projectChats).typingAccess(ALICE, ROOM);
	}

	@Test void cachesAccessPerConnectionUntilItExpires() {
		WebSocketSession tab = session();
		relay.relay(tab, ALICE, "direct", ROOM);
		clock.advance(Duration.ofSeconds(5));
		relay.relay(tab, ALICE, "direct", ROOM);
		verify(directChats, times(1)).directTypingRecipients(ALICE, ROOM);
		verify(dispatcher, times(2)).dispatch(List.of(BOB), TypingFrame.direct(ROOM));

		clock.advance(Duration.ofSeconds(5));
		when(directChats.directTypingRecipients(ALICE, ROOM)).thenReturn(Optional.empty());
		relay.relay(tab, ALICE, "direct", ROOM);
		verify(directChats, times(2)).directTypingRecipients(ALICE, ROOM);
		verify(dispatcher, times(2)).dispatch(any(), any());
	}

	@Test void refusedRoomIsCachedToo() {
		when(directChats.directTypingRecipients(ALICE, ROOM)).thenReturn(Optional.empty());
		WebSocketSession tab = session();
		relay.relay(tab, ALICE, "direct", ROOM);
		clock.advance(Duration.ofSeconds(2));
		relay.relay(tab, ALICE, "direct", ROOM);
		verify(directChats, times(1)).directTypingRecipients(ALICE, ROOM);
		verifyNoInteractions(dispatcher);
	}

	@Test void projectFrameCarriesTheNicknameAndSkipsEmptyRooms() {
		when(projectChats.typingAccess(ALICE, 5)).thenReturn(Optional.of(new ProjectChatService.TypingAccess(List.of(BOB, 3L), "앨리스")));
		when(projectChats.typingAccess(ALICE, 6)).thenReturn(Optional.of(new ProjectChatService.TypingAccess(List.of(), "앨리스")));
		relay.relay(session(), ALICE, "project", 5);
		relay.relay(session(), ALICE, "project", 6);
		verify(dispatcher).dispatch(List.of(BOB, 3L), TypingFrame.project(5, "앨리스"));
		verifyNoMoreInteractions(dispatcher);
	}

	@Test void oldIntervalsArePurged() {
		Map<Long, WebSocketSession> tabs = new HashMap<>();
		for (long member = 1; member <= 999; member++) {
			relay.relay(tabs.computeIfAbsent(member, ignored -> session()), member, "direct", ROOM);
		}
		assertEquals(999, relay.trackedIntervals());
		clock.advance(Duration.ofSeconds(2));
		relay.relay(session(), 5000, "direct", ROOM);
		assertEquals(1, relay.trackedIntervals());
	}
}
