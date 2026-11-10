package com.devmozip.backend.client;

import com.devmozip.backend.config.GeminiProperties;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiAnalysisClientTest {
	GeminiProperties properties;
	HttpClient http;
	GeminiAnalysisClient client;
	@BeforeEach void setup() throws Exception {
		properties = new GeminiProperties();
		properties.setApiKey("test-secret-not-for-logs");
		http = mock(HttpClient.class);
		client = new GeminiAnalysisClient(properties, new ObjectMapper(), http);
	}
	@SuppressWarnings("unchecked")
	void respond(int status, String body) {
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));
	}
	@Test void structuredResponseUsesHeaderAndFixedEndpoint() {
		String json = "{\"reply\":\"어떤 협업을 원하시나요?\",\"questions\":[\"기간은요?\"],\"changes\":[],\"facts\":[]}";
		respond(200, new ObjectMapper().writeValueAsString(java.util.Map.of("candidates", new Object[]{java.util.Map.of(
			"finishReason", "STOP", "content", java.util.Map.of("parts", new Object[]{java.util.Map.of("text", json)}))})));
		assertEquals(1, client.analyze("사용자 문맥").questions().size());
		var request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(http).sendAsync(request.capture(), any());
		assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent", request.getValue().uri().toString());
		assertTrue(request.getValue().uri().getQuery() == null);
		assertEquals(properties.getApiKey(), request.getValue().headers().firstValue("x-goog-api-key").orElseThrow());
		var bytes = new java.io.ByteArrayOutputStream();
		request.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
			@Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
			@Override public void onNext(java.nio.ByteBuffer b) { byte[] chunk = new byte[b.remaining()]; b.get(chunk); bytes.writeBytes(chunk); }
			@Override public void onError(Throwable error) { fail(error); }
			@Override public void onComplete() {}
		});
		var payload = new ObjectMapper().readTree(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
		assertEquals("application/json", payload.path("generationConfig").path("responseMimeType").asText());
		var wire = payload.path("generationConfig").path("responseJsonSchema");
		assertTrue(wire.path("properties").has("facts"));
		assertTrue(wire.path("properties").has("changes"));
		assertFalse(wire.toString().contains("maxItems"));
		assertFalse(wire.toString().contains("minimum"));
		assertFalse(wire.toString().contains("maximum"));
		assertTrue(payload.has("systemInstruction"));
		assertFalse(payload.has("tools"));
		assertFalse(payload.toString().contains(properties.getApiKey()));
	}
	@Test void providerSchemaDropsBoundsButLocalValidationKeepsThem() throws Exception {
		var mapper = new ObjectMapper();
		var full = mapper.readTree(new org.springframework.core.io.ClassPathResource("ai/analysis-schema.json").getInputStream());
		assertTrue(full.toString().contains("maxItems"));
		var wire = GeminiAnalysisClient.simplifyForProvider(mapper, full);
		assertFalse(wire.toString().contains("maxItems"));
		assertFalse(wire.toString().contains("\"minimum\""));
		assertTrue(wire.path("properties").path("changes").path("items").path("properties").has("count"));
		assertEquals(full.path("required"), wire.path("required"));
		String oversized = "{\"reply\":\"응답\",\"questions\":[" + "\"q\",".repeat(8) + "\"q\"],\"changes\":[],\"facts\":[]}";
		respond(200, mapper.writeValueAsString(java.util.Map.of("candidates", new Object[]{java.util.Map.of(
			"finishReason", "STOP", "content", java.util.Map.of("parts", new Object[]{java.util.Map.of("text", oversized)}))})));
		assertEquals(502, assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getStatusCode().value());
	}
	@Test void providerFieldViolationsAreWhitelistedWithoutFreeText() {
		respond(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"private schema exploded\",\"details\":["
			+ "{\"@type\":\"type.googleapis.com/google.rpc.BadRequest\",\"fieldViolations\":["
			+ "{\"field\":\"generationConfig.responseJsonSchema\",\"description\":\"private detail\"},"
			+ "{\"field\":\"evil; drop table\",\"description\":\"ignore\"},"
			+ "{\"field\":\"user.content\",\"description\":\"ignore\"}]}]}}");
		var error = assertThrows(ResponseStatusException.class, () -> client.analyze("private input"));
		assertEquals(502, error.getStatusCode().value());
		assertTrue(error.getReason().contains("구조화 응답 스키마"));
		assertTrue(error.getReason().contains("fields=generationConfig.responseJsonSchema"));
		assertFalse(error.getReason().contains("private"));
		assertFalse(error.getReason().contains("evil"));
		assertFalse(error.getReason().contains("user.content"));
	}
	@Test void missingKeyDoesNotCallGoogle() {
		properties.setApiKey("");
		assertEquals(503, assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getStatusCode().value());
		verifyNoInteractions(http);
	}
	@Test void quotaErrorDoesNotRetryOrExposeProviderBody() {
		respond(429, "test-secret-not-for-logs and user input");
		var error = assertThrows(ResponseStatusException.class, () -> client.analyze("private-text"));
		assertEquals(429, error.getStatusCode().value());
		assertFalse(error.toString().contains("test-secret"));
		assertNull(error.getCause());
		verify(http, times(1)).sendAsync(any(), any());
	}
	@Test void invalidJsonBlockedAndTruncatedResponsesFailClosed() {
		for (String body : new String[]{"not-json", "{}", "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
			"{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}"}) {
			respond(200, body);
			assertEquals(502, assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getStatusCode().value());
		}
	}
	@Test @SuppressWarnings("unchecked") void timeoutCancelsAndDoesNotRetry() {
		properties.setTimeout(Duration.ofMillis(10));
		var pending = new CompletableFuture<HttpResponse<String>>();
		when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
		assertEquals(504, assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getStatusCode().value());
		assertTrue(pending.isCancelled());
		verify(http, times(1)).sendAsync(any(), any());
	}
	@Test void upstreamFailureIsSanitized() {
		respond(500, "private provider error");
		var error = assertThrows(ResponseStatusException.class, () -> client.analyze("text"));
		assertEquals(502, error.getStatusCode().value());
		assertNull(error.getCause());
	}
	@Test void unknownOrCoercedFieldsAreRejectedBeforeDtoConversion() {
		for (String json : new String[]{
			"{\"reply\":123,\"questions\":[],\"changes\":[],\"facts\":[]}",
			"{\"reply\":\"응답\",\"questions\":[],\"changes\":[],\"facts\":[],\"score\":100}",
			"{\"reply\":\"응답\",\"questions\":[],\"changes\":[{\"field\":\"roles\",\"action\":\"ADD\",\"values\":[\"백엔드\"],\"count\":\"1\",\"origin\":\"EXPLICIT\",\"evidence\":\"백엔드 1명\",\"explicitChange\":true}],\"facts\":[]}"}) {
			respond(200, new ObjectMapper().writeValueAsString(java.util.Map.of("candidates", new Object[]{java.util.Map.of(
				"finishReason", "STOP", "content", java.util.Map.of("parts", new Object[]{java.util.Map.of("text", json)}))})));
			assertEquals(502, assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getStatusCode().value());
		}
	}
	@Test void opaqueAuthKeyWithDotsIsSentWithoutChangingTheCredential() {
		properties.setApiKey("AQ.synthetic.auth-key_value-12345");
		respond(429, "{}");
		assertEquals(429, assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getStatusCode().value());
		var request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(http).sendAsync(request.capture(), any());
		assertEquals(properties.getApiKey(), request.getValue().headers().firstValue("x-goog-api-key").orElseThrow());
		assertFalse(request.getValue().uri().toString().contains(properties.getApiKey()));
	}
	@Test void unsafeKeyIsRejectedLocallyWithoutExposingIt() {
		for (String key : new String[]{"key with spaces", "key\r\nInjected: header", "비밀키", "x".repeat(4097)}) {
			properties.setApiKey(key);
			var error = assertThrows(ResponseStatusException.class, () -> client.analyze("text"));
			assertEquals(503, error.getStatusCode().value());
			assertTrue(error.getReason().contains("API 키"));
			assertFalse(error.getReason().contains(key));
		}
		verifyNoInteractions(http);
	}
	@Test void configurationErrorsIdentifyTheFieldWithoutPrintingValues() {
		properties.setModel("models/invalid-model");
		assertTrue(assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getReason().contains("모델명"));
		properties.setModel("gemini-3.5-flash-lite");
		properties.setTimeout(Duration.ofSeconds(61));
		assertTrue(assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getReason().contains("제한 시간"));
		properties.setTimeout(Duration.ofSeconds(25));
		properties.setMaxOutputTokens(8193);
		assertTrue(assertThrows(ResponseStatusException.class, () -> client.analyze("text")).getReason().contains("출력 토큰"));
		verifyNoInteractions(http);
	}
	@Test void providerRejectionsIdentifyStatusWithoutExposingFreeTextOrUnknownCodes() {
		for (int status : new int[]{400, 401, 403, 404, 500, 503}) {
			respond(status, "{\"error\":{\"message\":\"test-secret-not-for-logs private input\",\"status\":\"secret-status\",\"details\":[{\"reason\":\"secret-reason\"}]}}");
			var error = assertThrows(ResponseStatusException.class, () -> client.analyze("private input"));
			assertEquals(502, error.getStatusCode().value());
			assertTrue(error.getReason().contains("Google HTTP " + status));
			assertFalse(error.getReason().contains("secret"));
			assertFalse(error.getReason().contains("private input"));
			assertNull(error.getCause());
		}
	}
	@Test void providerKnownReasonsAreWhitelistedAndActionable() {
		for (String reason : new String[]{"API_KEY_INVALID", "ACCESS_TOKEN_TYPE_UNSUPPORTED", "SERVICE_DISABLED", "BILLING_DISABLED"}) {
			respond(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"private details\",\"details\":[{\"reason\":\"" + reason + "\",\"metadata\":{\"credential\":\"private-key\"}}]}}");
			var error = assertThrows(ResponseStatusException.class, () -> client.analyze("text"));
			assertTrue(error.getReason().contains("INVALID_ARGUMENT"));
			assertTrue(error.getReason().contains(reason));
			assertFalse(error.getReason().contains("private"));
		}
	}
	@Test void providerHtmlErrorStillExposesOnlyHttpStatus() {
		respond(503, "<html>private provider response</html>");
		var error = assertThrows(ResponseStatusException.class, () -> client.analyze("text"));
		assertEquals(502, error.getStatusCode().value());
		assertTrue(error.getReason().contains("Google HTTP 503"));
		assertFalse(error.getReason().contains("private"));
	}
}
