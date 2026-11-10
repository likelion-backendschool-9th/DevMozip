package com.devmozip.backend.client;

import com.devmozip.backend.config.GeminiProperties;
import com.devmozip.backend.dto.ai.GeminiAnalysis;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * 고정 Google 엔드포인트, 헤더 인증, 단일 호출.
 * 요청·응답 원문과 키는 로그로 남기지 않는다. 구조화 응답은 공식 responseMimeType/responseJsonSchema를 쓴다.
 * Google 스키마 복잡도 예산 때문에 전송용 스키마에서는 배열·수치 한계를 제거하고, 앱에서 동일 한계를 검증한다.
 */
@Component
public class GeminiAnalysisClient {
	private static final java.util.Set<String> BOUND_KEYS = java.util.Set.of(
		"maxItems", "minItems", "minimum", "maximum", "minLength", "maxLength", "minProperties", "maxProperties", "uniqueItems");
	private final GeminiProperties properties;
	private final ObjectMapper mapper;
	private final HttpClient http;
	private final String instruction;
	/** 앱 검증용 전체 JSON Schema. */
	private final tools.jackson.databind.JsonNode schema;
	@Autowired
	public GeminiAnalysisClient(GeminiProperties properties, ObjectMapper mapper) throws java.io.IOException {
		this(properties, mapper, HttpClient.newBuilder().connectTimeout(safeConnectTimeout(properties))
			.followRedirects(HttpClient.Redirect.NEVER).build());
	}
	GeminiAnalysisClient(GeminiProperties properties, ObjectMapper mapper, HttpClient http) throws java.io.IOException {
		this.properties = properties;
		this.mapper = mapper;
		this.http = http;
		try (var input = new ClassPathResource("ai/analysis-instruction.txt").getInputStream()) {
			instruction = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		try (var input = new ClassPathResource("ai/analysis-schema.json").getInputStream()) {
			schema = mapper.readTree(input);
		}
	}
	public GeminiAnalysis analyze(String context) {
		return mapper.treeToValue(generate(context, instruction, schema), GeminiAnalysis.class);
	}
	/** 별도 역할·스키마를 사용하는 추천 근거 비교도 동일한 제한·오류 처리를 따른다. */
	public tools.jackson.databind.JsonNode generate(String context, String instruction, tools.jackson.databind.JsonNode schema) {
		if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "자연어 분석이 설정되지 않았습니다. 직접 조건을 선택할 수 있습니다.");
		}
		// 키는 opaque credential이다. Google 발급 형식을 추측하지 않고 HTTP 헤더 안전성만 확인한다.
		if (properties.getApiKey().length() > 4096
			|| !properties.getApiKey().chars().allMatch(c -> c >= 33 && c <= 126)) {
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API 키에 공백·줄바꿈이 포함되어 있거나 헤더로 전달할 수 없는 형식입니다. 키만 다시 입력해 주세요.");
		}
		if (properties.getModel() == null || !properties.getModel().matches("gemini-[a-zA-Z0-9.-]{1,80}")) {
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "Gemini 모델명 설정을 확인해 주세요.");
		}
		if (properties.getConnectTimeout().isNegative() || properties.getConnectTimeout().isZero()
			|| properties.getTimeout().isNegative() || properties.getTimeout().isZero()
			|| properties.getTimeout().compareTo(java.time.Duration.ofSeconds(60)) > 0) {
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "Gemini 연결 제한 시간은 양수, 호출 제한 시간은 0초 초과 60초 이하여야 합니다.");
		}
		if (properties.getMaxOutputTokens() < 256 || properties.getMaxOutputTokens() > 8192) {
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "Gemini 최대 출력 토큰은 256~8192로 설정해 주세요.");
		}
		var payload = Map.of("systemInstruction", Map.of("parts", new Object[]{Map.of("text", instruction)}),
			"contents", new Object[]{Map.of("role", "user", "parts", new Object[]{Map.of("text", context)})},
			"generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", simplifyForProvider(mapper, schema),
				"maxOutputTokens", properties.getMaxOutputTokens()));
		var request = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
			+ properties.getModel() + ":generateContent"))
			.timeout(properties.getTimeout()).header("Content-Type", "application/json")
			.header("x-goog-api-key", properties.getApiKey())
			.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
		var pending = http.sendAsync(request, info -> new LimitedBody());
		try {
			var response = pending.get(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
			if (response.statusCode() != 200) throw upstreamFailure(response.statusCode(), response.body());
			if (response.body() == null || response.body().length() > 100000) throw new IllegalArgumentException();
			var candidates = mapper.readTree(response.body()).path("candidates");
			if (candidates.size() != 1 || !"STOP".equals(candidates.get(0).path("finishReason").asText())) throw new IllegalArgumentException();
			var parts = candidates.get(0).path("content").path("parts");
			var text = new StringBuilder();
			for (var part : parts) if (!part.path("thought").asBoolean(false) && part.path("text").isString()) text.append(part.path("text").asText());
			var analysis = mapper.readTree(text.toString());
			validate(analysis, schema);
			return analysis;
		} catch (ResponseStatusException expected) {
			throw expected;
		} catch (java.util.concurrent.TimeoutException timeout) {
			pending.cancel(true);
			throw error(HttpStatus.GATEWAY_TIMEOUT, "AI 분석 시간이 초과되었습니다. 같은 메시지 키로 다시 시도할 수 있습니다.");
		} catch (InterruptedException interrupted) {
			pending.cancel(true);
			Thread.currentThread().interrupt();
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "AI 분석이 중단되었습니다.");
		} catch (Exception invalid) {
			if (invalid instanceof java.util.concurrent.ExecutionException && invalid.getCause() instanceof java.net.http.HttpTimeoutException) {
				throw error(HttpStatus.GATEWAY_TIMEOUT, "AI 분석 시간이 초과되었습니다.");
			}
			throw error(HttpStatus.BAD_GATEWAY, "AI 분석 응답을 확인할 수 없습니다. 조건은 변경되지 않았습니다.");
		}
	}
	/**
	 * Google structured output 문서는 maxItems/minimum 등이 출력 문법 상태 공간을 키워
	 * 400 INVALID_ARGUMENT를 유발할 수 있다고 안내한다. 전송용 복사본에서만 제거하고 앱 검증은 유지한다.
	 */
	static tools.jackson.databind.JsonNode simplifyForProvider(ObjectMapper mapper, tools.jackson.databind.JsonNode source) {
		if (source == null || source.isNull() || source.isMissingNode()) return source;
		if (source.isObject()) {
			var copy = mapper.createObjectNode();
			source.propertyStream().forEach(entry -> {
				if (!BOUND_KEYS.contains(entry.getKey())) copy.set(entry.getKey(), simplifyForProvider(mapper, entry.getValue()));
			});
			return copy;
		}
		if (source.isArray()) {
			var copy = mapper.createArrayNode();
			source.forEach(item -> copy.add(simplifyForProvider(mapper, item)));
			return copy;
		}
		return source.deepCopy();
	}
	/** HTTP 상태와 허용 목록의 코드·필드 경로만 노출한다. message/metadata/자유 텍스트는 반환하지 않는다. */
	private ResponseStatusException upstreamFailure(int httpStatus, String body) {
		var statuses = java.util.Set.of("INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND",
			"FAILED_PRECONDITION", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL", "DEADLINE_EXCEEDED");
		var reasons = java.util.Set.of("API_KEY_INVALID", "API_KEY_EXPIRED", "API_KEY_SERVICE_BLOCKED",
			"API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED", "SERVICE_DISABLED",
			"ACCESS_TOKEN_TYPE_UNSUPPORTED", "CREDENTIALS_MISSING", "BILLING_DISABLED", "LOCATION_POLICY_VIOLATED");
		var fieldAllow = java.util.Set.of("generationconfig", "responsejsonschema", "responseschema",
			"responsemimetype", "responseformat", "systeminstruction", "contents", "model", "maxoutputtokens");
		String status = "";
		String reason = "";
		var fields = new java.util.LinkedHashSet<String>();
		try {
			var upstream = mapper.readTree(body).path("error");
			String code = upstream.path("status").asText();
			if (statuses.contains(code)) status = code;
			for (var detail : upstream.path("details")) {
				String value = detail.path("reason").asText();
				if (reasons.contains(value) && reason.isEmpty()) reason = value;
				for (var violation : detail.path("fieldViolations")) {
					String field = violation.path("field").asText("");
					if (field.length() > 120 || !field.chars().allMatch(c ->
						(c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
							|| c == '.' || c == '_' || c == '[' || c == ']')) continue;
					String lower = field.toLowerCase(java.util.Locale.ROOT);
					if (fieldAllow.stream().anyMatch(lower::contains)) fields.add(field);
				}
			}
		} catch (RuntimeException unreadable) { /* HTML/손상된 JSON도 HTTP 상태만으로 안내한다. */ }
		String hint = switch (httpStatus) {
			case 400 -> "Gemini가 요청 형식을 거절했습니다. 요청 JSON·구조화 응답 설정과 모델 지원 여부를 확인해야 합니다.";
			case 401 -> "Gemini API 키 인증이 거절되었습니다. AI Studio의 키 상태를 확인해 주세요.";
			case 403 -> "Gemini API 접근 권한이 거절되었습니다. 키의 API 제한·프로젝트 권한·제공 지역을 확인해 주세요.";
			case 404 -> "설정한 Gemini 모델 또는 generateContent 호출을 사용할 수 없습니다. GEMINI_MODEL과 계정의 모델 제공 여부를 확인해 주세요.";
			case 429 -> "AI 무료 할당량 또는 요청 제한에 도달했습니다. 잠시 후 다시 시도해 주세요.";
			default -> "Gemini 서버가 요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.";
		};
		if (reason.equals("API_KEY_INVALID") || reason.equals("API_KEY_EXPIRED")) {
			hint = "Google이 Gemini API 키를 유효하지 않거나 만료된 키로 판정했습니다. AI Studio의 키 상태를 확인해 주세요.";
		} else if (reason.equals("ACCESS_TOKEN_TYPE_UNSUPPORTED")) {
			hint = "Google이 인증 키 유형을 거절했습니다. AI Studio 키 상태와 해당 API의 인증 지원을 확인해 주세요.";
		} else if (reason.equals("SERVICE_DISABLED")) {
			hint = "Google 프로젝트의 Gemini API 활성화 상태와 키의 API 제한을 확인해 주세요.";
		} else if (status.equals("FAILED_PRECONDITION") || reason.equals("BILLING_DISABLED")) {
			hint = "Google 프로젝트의 호출 선행 조건을 충족하지 못했습니다. 무료 티어 제공 여부·지역·프로젝트 상태를 확인해 주세요. 유료 전환은 자동 진행하지 않습니다.";
		} else if (httpStatus == 400 && fields.stream().anyMatch(f -> f.toLowerCase(java.util.Locale.ROOT).contains("responsejsonschema"))) {
			hint = "Gemini가 구조화 응답 스키마를 거절했습니다. 스키마 복잡도·지원 키워드를 확인해야 합니다.";
		}
		String code = "Google HTTP " + httpStatus + (status.isEmpty() ? "" : " / " + status) + (reason.isEmpty() ? "" : " / " + reason);
		if (!fields.isEmpty()) code += " / fields=" + String.join(",", fields);
		return error(httpStatus == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, hint + " (" + code + ")");
	}
	private static java.time.Duration safeConnectTimeout(GeminiProperties properties) {
		var value = properties.getConnectTimeout();
		return value.isNegative() || value.isZero() ? java.time.Duration.ofSeconds(3) : value;
	}
	/** JSON 타입 자동 변환·알 수 없는 필드·누락 키를 DTO 변환 전에 거절한다. */
	private static void validate(tools.jackson.databind.JsonNode value, tools.jackson.databind.JsonNode spec) {
		if (value == null) throw new IllegalArgumentException();
		var type = spec.path("type");
		if (value.isNull() && type.isArray() && type.valueStream().anyMatch(t -> "null".equals(t.asText()))) return;
		String expected = type.isArray() ? type.get(0).asText() : type.asText();
		boolean valid = switch (expected) {
			case "object" -> value.isObject();
			case "array" -> value.isArray();
			case "string" -> value.isString();
			case "integer" -> value.isIntegralNumber() && value.canConvertToInt();
			case "boolean" -> value.isBoolean();
			default -> false;
		};
		if (!valid) throw new IllegalArgumentException();
		if (spec.has("enum") && spec.path("enum").valueStream().noneMatch(value::equals)) throw new IllegalArgumentException();
		if (value.isObject()) {
			var props = spec.path("properties");
			if (value.propertyStream().anyMatch(p -> !props.has(p.getKey()))) throw new IllegalArgumentException();
			for (var key : spec.path("required")) validate(value.get(key.asText()), props.get(key.asText()));
		} else if (value.isArray()) {
			if (value.size() > spec.path("maxItems").asInt(100)) throw new IllegalArgumentException();
			for (var item : value) validate(item, spec.path("items"));
		} else if (value.isIntegralNumber() && (value.asInt() < spec.path("minimum").asInt(Integer.MIN_VALUE)
			|| value.asInt() > spec.path("maximum").asInt(Integer.MAX_VALUE))) throw new IllegalArgumentException();
	}
	private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
		private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
		private java.util.concurrent.Flow.Subscription subscription;
		private long bytes;
		@Override public java.util.concurrent.CompletionStage<String> getBody() { return delegate.getBody(); }
		@Override public void onSubscribe(java.util.concurrent.Flow.Subscription next) { subscription = next; delegate.onSubscribe(next); }
		@Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
			bytes += buffers.stream().mapToLong(java.nio.ByteBuffer::remaining).sum();
			if (bytes > 100000) { subscription.cancel(); delegate.onError(new IllegalArgumentException()); }
			else delegate.onNext(buffers);
		}
		@Override public void onError(Throwable error) { delegate.onError(error); }
		@Override public void onComplete() { delegate.onComplete(); }
	}
	private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
