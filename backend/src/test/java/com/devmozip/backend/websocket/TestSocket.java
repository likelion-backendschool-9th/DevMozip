package com.devmozip.backend.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 실제 WebSocket 클라이언트. 브라우저처럼 쿠키와 Origin을 핸드셰이크에 넣는다. */
final class TestSocket implements WebSocket.Listener {
	static final String ALLOWED_ORIGIN = "http://localhost:3000";
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final String PING = "{\"type\":\"ping\"}";
	private static final String PONG = "{\"type\":\"pong\"}";

	/** 핑을 제외한 수신 프레임 */
	final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
	final AtomicInteger pings = new AtomicInteger();
	final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
	volatile String closeReason;
	volatile Instant closedAt;
	private final StringBuilder partial = new StringBuilder();
	private final boolean answersPing;
	private final boolean reads;
	private WebSocket socket;

	private TestSocket(boolean answersPing, boolean reads) {
		this.answersPing = answersPing;
		this.reads = reads;
	}

	static TestSocket connect(int port, String token) throws Exception {
		return open(port, token, new TestSocket(true, true));
	}

	/** 핑에 답하지 않는 클라이언트 */
	static TestSocket connectSilent(int port, String token) throws Exception {
		return open(port, token, new TestSocket(false, true));
	}

	/** 수신을 멈춘 클라이언트. 서버 쪽 전송이 막힌다. */
	static TestSocket connectNotReading(int port, String token) throws Exception {
		return open(port, token, new TestSocket(false, false));
	}

	private static TestSocket open(int port, String token, TestSocket listener) throws Exception {
		builder(token, ALLOWED_ORIGIN).buildAsync(uri(port, "/ws/messages"), listener).get(10, TimeUnit.SECONDS);
		return listener;
	}

	/** 핸드셰이크 응답 코드. 연결되면 닫고 101을 돌려준다. */
	static int handshakeStatus(int port, String path, String token, String origin) throws Exception {
		TestSocket listener = new TestSocket(true, true);
		try {
			WebSocket opened = builder(token, origin).buildAsync(uri(port, path), listener).get(10, TimeUnit.SECONDS);
			opened.abort();
			return 101;
		} catch (ExecutionException e) {
			if (e.getCause() instanceof WebSocketHandshakeException rejected) {
				return rejected.getResponse().statusCode();
			}
			throw e;
		}
	}

	private static WebSocket.Builder builder(String token, String origin) {
		WebSocket.Builder builder = CLIENT.newWebSocketBuilder();
		if (token != null) {
			builder.header("Cookie", "devmozip_access=" + token);
		}
		if (origin != null) {
			builder.header("Origin", origin);
		}
		return builder;
	}

	private static URI uri(int port, String path) {
		return URI.create("ws://localhost:" + port + path);
	}

	void sendText(String text) throws Exception {
		socket.sendText(text, true).get(5, TimeUnit.SECONDS);
	}

	void sendBinary(byte[] bytes) throws Exception {
		socket.sendBinary(ByteBuffer.wrap(bytes), true).get(5, TimeUnit.SECONDS);
	}

	int awaitClose() throws Exception {
		return closeCode.get(10, TimeUnit.SECONDS);
	}

	boolean isClosed() {
		return closeCode.isDone();
	}

	void abort() {
		socket.abort();
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		socket = webSocket;
		if (reads) {
			webSocket.request(1);
		}
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		partial.append(data);
		if (last) {
			String frame = partial.toString();
			partial.setLength(0);
			if (PING.equals(frame)) {
				pings.incrementAndGet();
				if (answersPing) {
					webSocket.sendText(PONG, true);
				}
			} else {
				frames.add(frame);
			}
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		closeReason = reason;
		closedAt = Instant.now();
		closeCode.complete(statusCode);
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		closeCode.completeExceptionally(error);
	}
}
