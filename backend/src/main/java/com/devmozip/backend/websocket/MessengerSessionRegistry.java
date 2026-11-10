package com.devmozip.backend.websocket;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.config.MessengerRealtimeProperties;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

/**
 * 회원별 WebSocket 연결을 메모리에 둔다. 탭마다 연결이 하나이고 대화방 수나 탭 수를 제한하지 않는다.
 * 연결 확인, 토큰 만료, 정지 확인을 스레드 하나에서 함께 처리한다. 스레드 풀을 빈으로 등록하면
 * 스프링 부트의 applicationTaskExecutor 자동 구성이 빠지므로 여기서 직접 만든다.
 */
@Slf4j
@Component
public class MessengerSessionRegistry implements SmartLifecycle {
	public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "token expired");
	public static final CloseStatus SUSPENDED = new CloseStatus(4403, "suspended");
	static final CloseStatus SHUTTING_DOWN = CloseStatus.GOING_AWAY.withReason("server shutting down");
	private static final TextMessage PING = new TextMessage("{\"type\":\"ping\"}");
	private static final String CONNECTION = "messenger.connection";
	private static final int SEND_BUFFER_LIMIT = 64 * 1024;
	// Tomcat의 기본 전송 대기 시간은 20초다. 멈춘 연결 하나가 다른 연결의 전달을 그만큼 막지 않게 줄인다.
	private static final String TOMCAT_SEND_TIMEOUT = "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";

	private final Map<Long, Set<Connection>> connections = new ConcurrentHashMap<>();
	private final MessengerRealtimeProperties properties;
	private final SuspendedMemberStore suspendedMembers;
	private final Clock clock;
	private final com.devmozip.backend.repository.MemberRepository accounts;
	private volatile boolean running;
	private ScheduledExecutorService checker;

	public MessengerSessionRegistry(MessengerRealtimeProperties properties, SuspendedMemberStore suspendedMembers,
		@Qualifier("messengerClock") Clock clock) {
		this(properties, suspendedMembers, clock, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public MessengerSessionRegistry(MessengerRealtimeProperties properties, SuspendedMemberStore suspendedMembers,
		@Qualifier("messengerClock") Clock clock, com.devmozip.backend.repository.MemberRepository accounts) {
		this.properties = properties;
		this.suspendedMembers = suspendedMembers;
		this.clock = clock;
		this.accounts = accounts;
	}

	void register(WebSocketSession session) {
		Connection connection = new Connection(session, clock.instant(), properties);
		session.getAttributes().put(CONNECTION, connection);
		connections.compute(connection.memberId, (id, tabs) -> {
			Set<Connection> next = tabs == null ? ConcurrentHashMap.newKeySet() : tabs;
			next.add(connection);
			return next;
		});
		// 넣은 뒤에 확인해야 종료 중에 들어온 연결을 놓치지 않는다.
		if (!running) {
			connection.close(SHUTTING_DOWN);
		}
	}

	void unregister(WebSocketSession session) {
		Connection connection = connection(session);
		if (connection == null) {
			return;
		}
		connections.computeIfPresent(connection.memberId, (id, tabs) -> {
			tabs.remove(connection);
			return tabs.isEmpty() ? null : tabs;
		});
	}

	void markPong(WebSocketSession session) {
		Connection connection = connection(session);
		if (connection != null) {
			connection.lastPong = clock.instant();
		}
	}

	void close(WebSocketSession session, CloseStatus status) {
		Connection connection = connection(session);
		if (connection != null) {
			connection.close(status);
		}
	}

	/** 정지됐거나 토큰이 만료된 연결이면 닫고 true를 돌려준다. */
	boolean closeIfNotAllowed(WebSocketSession session) {
		Connection connection = connection(session);
		return connection != null && closeIfNotAllowed(connection);
	}

	public void send(Collection<Long> memberIds, String frame) {
		memberIds.forEach(memberId -> send(memberId.longValue(), frame));
	}

	/** 이 서버에 연결된 해당 회원의 모든 탭에 전달한다. 연결이 없으면 아무 일도 하지 않는다. */
	public void send(long memberId, String frame) {
		Set<Connection> tabs = connections.get(memberId);
		if (tabs == null) {
			return;
		}
		TextMessage message = new TextMessage(frame);
		for (Connection connection : tabs) {
			if (!closeIfNotAllowed(connection)) {
				deliver(connection, message);
			}
		}
	}

	int connectionCount(long memberId) {
		Set<Connection> tabs = connections.get(memberId);
		return tabs == null ? 0 : tabs.size();
	}

	void checkConnections() {
		for (Set<Connection> tabs : connections.values()) {
			for (Connection connection : tabs) {
				try {
					check(connection);
				} catch (RuntimeException e) {
					// 연결 하나의 문제로 나머지 연결의 확인이 멈추면 안 된다.
					log.warn("메신저 연결 확인에 실패했습니다: {}", e.getClass().getSimpleName());
				}
			}
		}
	}

	private void check(Connection connection) {
		if (closeIfNotAllowed(connection)) {
			return;
		}
		Instant now = clock.instant();
		if (!now.isBefore(connection.lastPong.plus(properties.getPongTimeout()))) {
			connection.close(CloseStatus.SESSION_NOT_RELIABLE);
		} else if (!now.isBefore(connection.lastPing.plus(properties.getPingInterval()))) {
			connection.lastPing = now;
			deliver(connection, PING);
		}
	}

	private boolean closeIfNotAllowed(Connection connection) {
		try {
			if (accounts != null && accounts.findByEmail(connection.email).filter(member -> member.getId() == connection.memberId).isEmpty()) {
				connection.close(TOKEN_EXPIRED);
				return true;
			}
		} catch (org.springframework.dao.DataAccessException e) {
			connection.close(CloseStatus.SESSION_NOT_RELIABLE);
			return true;
		}
		if (suspendedMembers.isSuspended(connection.email)) {
			connection.close(SUSPENDED);
			return true;
		}
		if (!clock.instant().isBefore(connection.tokenExpiresAt)) {
			connection.close(TOKEN_EXPIRED);
			return true;
		}
		return false;
	}

	private static void deliver(Connection connection, TextMessage message) {
		try {
			connection.session.sendMessage(message);
		} catch (SessionLimitExceededException e) {
			connection.close(e.getStatus());
		} catch (IOException | RuntimeException e) {
			// 연결 하나의 실패가 같은 회원의 다른 탭이나 다른 회원의 전달을 막으면 안 된다.
			connection.close(CloseStatus.SESSION_NOT_RELIABLE);
		}
	}

	private static Connection connection(WebSocketSession session) {
		return (Connection) session.getAttributes().get(CONNECTION);
	}

	/** 다른 수명 주기보다 먼저 멈춰(가장 높은 단계), 웹 서버가 닫기 전에 클라이언트에게 1001을 보낸다. */
	@Override
	public void start() {
		running = true;
		long interval = properties.getCheckInterval().toMillis();
		checker = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "messenger-websocket-check");
			thread.setDaemon(true);
			return thread;
		});
		checker.scheduleWithFixedDelay(this::checkConnections, interval, interval, TimeUnit.MILLISECONDS);
	}

	@Override
	public void stop() {
		running = false;
		if (checker != null) {
			checker.shutdownNow();
		}
		connections.values().forEach(tabs -> tabs.forEach(connection -> connection.close(SHUTTING_DOWN)));
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private static final class Connection {
		private final WebSocketSession session;
		private final long memberId;
		private final String email;
		private final Instant tokenExpiresAt;
		private volatile Instant lastPong;
		private volatile Instant lastPing;

		Connection(WebSocketSession session, Instant now, MessengerRealtimeProperties properties) {
			Map<String, Object> attributes = session.getAttributes();
			this.memberId = (Long) attributes.get(MessengerHandshakeInterceptor.MEMBER_ID);
			this.email = (String) attributes.get(MessengerHandshakeInterceptor.EMAIL);
			this.tokenExpiresAt = (Instant) attributes.get(MessengerHandshakeInterceptor.TOKEN_EXPIRES_AT);
			this.lastPong = now;
			this.lastPing = now;
			long sendTimeLimit = properties.getSendTimeLimit().toMillis();
			if (session instanceof NativeWebSocketSession nativeSession) {
				Session webSocket = nativeSession.getNativeSession(Session.class);
				if (webSocket != null) {
					webSocket.getUserProperties().put(TOMCAT_SEND_TIMEOUT, sendTimeLimit);
				}
			}
			// 여러 스레드의 전송을 직렬화하고, 받지 못하는 연결은 시간이나 버퍼 한도를 넘으면 닫는다.
			this.session = new ConcurrentWebSocketSessionDecorator(session, (int) sendTimeLimit, SEND_BUFFER_LIMIT);
		}

		void close(CloseStatus status) {
			try {
				session.close(status);
			} catch (IOException | RuntimeException e) {
				// 이미 끊긴 연결이다. 정리는 afterConnectionClosed에서 한다.
				log.debug("메신저 연결을 닫지 못했습니다: {}", e.getClass().getSimpleName());
			}
		}
	}
}
