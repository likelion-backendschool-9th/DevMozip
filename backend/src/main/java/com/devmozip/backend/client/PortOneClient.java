package com.devmozip.backend.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.devmozip.backend.client.PortOneException.Kind;
import com.devmozip.backend.config.PaymentProperties;

import tools.jackson.databind.JsonNode;

/**
 * 포트원 V2 REST API 호출 (docs/premium-payment-plan.md 3절).
 * 인증은 "Authorization: PortOne {API Secret}"이며, 로그에는 동작·상태 코드·오류 유형만 남긴다.
 * 빌링키는 경로에 들어가지만 RestClient는 요청 주소를 로그에 남기지 않는다.
 */
@Component
public class PortOneClient {
	private static final Logger log = LoggerFactory.getLogger(PortOneClient.class);
	private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([A-Z_]+)\"");
	private static final Pattern PG_CODE = Pattern.compile("\"pgCode\"\\s*:\\s*\"([^\"]{1,40})\"");

	/** 빌링키 조회 결과. display는 화면에 보여 줄 카드사·마스킹 번호 또는 간편결제 이름이다 */
	public record BillingKeyInfo(String status, String issueId, List<String> channelKeys, String display) {
		public boolean issued() { return "ISSUED".equals(status); }
	}

	/** 빌링키 결제 성공. paidAt은 포트원 응답의 ISO-8601 시각이다 */
	public record PaidPayment(String pgTxId, String paidAt) {
	}

	private final PaymentProperties properties;
	private final RestClient restClient;

	@Autowired
	public PortOneClient(PaymentProperties properties) {
		this(properties, RestClient.builder().requestFactory(requestFactory()));
	}

	PortOneClient(PaymentProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.baseUrl(properties.getApiBaseUrl()).build();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		// 결제 승인은 결제대행사를 거치므로 조회보다 오래 걸릴 수 있다
		factory.setReadTimeout(Duration.ofSeconds(20));
		return factory;
	}

	/** 빌링키의 발급 상태·채널·발급 주문 번호를 조회한다 */
	public BillingKeyInfo getBillingKey(String billingKey) {
		JsonNode body = call("빌링키 조회", () -> restClient.get()
				.uri(uri -> uri.path("/billing-keys/{billingKey}").queryParam("storeId", properties.getStoreId()).build(billingKey))
				.headers(headers -> headers.set("Authorization", authorization()))
				.retrieve()
				.body(JsonNode.class));
		if (body == null || !body.hasNonNull("status")) throw unexpected("빌링키 조회");
		List<String> channelKeys = new ArrayList<>();
		for (JsonNode channel : body.path("channels")) {
			if (channel.hasNonNull("key")) channelKeys.add(channel.get("key").asString());
		}
		String issueId = body.hasNonNull("issueId") ? body.get("issueId").asString() : null;
		return new BillingKeyInfo(body.get("status").asString(), issueId, List.copyOf(channelKeys), display(body.path("methods")));
	}

	/** 빌링키를 삭제한다. 이미 삭제됐거나 없는 빌링키는 삭제된 것으로 본다 */
	public void deleteBillingKey(String billingKey) {
		try {
			call("빌링키 삭제", () -> restClient.delete()
					.uri(uri -> uri.path("/billing-keys/{billingKey}").queryParam("storeId", properties.getStoreId()).build(billingKey))
					.headers(headers -> headers.set("Authorization", authorization()))
					.retrieve()
					.toBodilessEntity());
		} catch (PortOneException e) {
			if (e.getKind() == Kind.NOT_FOUND || "BILLING_KEY_ALREADY_DELETED".equals(e.getType())) return;
			throw e;
		}
	}

	/** 빌링키로 결제한다. paymentId는 우리 주문 번호이며, 같은 paymentId의 두 번째 결제는 포트원이 거절한다 */
	public PaidPayment payWithBillingKey(String paymentId, String billingKey, String orderName, String customerId, long amount, String currency) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("storeId", properties.getStoreId());
		request.put("billingKey", billingKey);
		request.put("orderName", orderName);
		if (StringUtils.hasText(customerId)) request.put("customer", Map.of("id", customerId));
		request.put("amount", Map.of("total", amount));
		request.put("currency", currency);
		JsonNode body = call("빌링키 결제", () -> restClient.post()
				.uri("/payments/{paymentId}/billing-key", paymentId)
				.headers(headers -> headers.set("Authorization", authorization()))
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(JsonNode.class));
		JsonNode payment = body == null ? null : body.get("payment");
		if (payment == null || !payment.hasNonNull("pgTxId") || !payment.hasNonNull("paidAt")) throw unexpected("빌링키 결제");
		return new PaidPayment(payment.get("pgTxId").asString(), payment.get("paidAt").asString());
	}

	/** 결제 상태(PAID, FAILED, CANCELLED 등)를 조회한다. 결과를 모르는 결제를 확인할 때 쓴다 */
	public String getPaymentStatus(String paymentId) {
		JsonNode body = call("결제 조회", () -> restClient.get()
				.uri(uri -> uri.path("/payments/{paymentId}").queryParam("storeId", properties.getStoreId()).build(paymentId))
				.headers(headers -> headers.set("Authorization", authorization()))
				.retrieve()
				.body(JsonNode.class));
		if (body == null || !body.hasNonNull("status")) throw unexpected("결제 조회");
		return body.get("status").asString();
	}

	/** 결제를 전액 취소한다. 결제 후 저장에 실패했을 때 되돌리는 용도다 */
	public void cancelPayment(String paymentId, String reason) {
		call("결제 취소", () -> restClient.post()
				.uri("/payments/{paymentId}/cancel", paymentId)
				.headers(headers -> headers.set("Authorization", authorization()))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("storeId", properties.getStoreId(), "reason", reason))
				.retrieve()
				.toBodilessEntity());
	}

	private String authorization() {
		return "PortOne " + properties.getApiSecret();
	}

	private <T> T call(String action, Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			String body = e.getResponseBodyAsString();
			String type = match(TYPE, body);
			log.warn("포트원 {} 실패: status={}, type={}, pgCode={}", action, status, type, match(PG_CODE, body));
			throw new PortOneException(kind(status, type), status, type);
		} catch (RestClientException e) {
			log.warn("포트원 {} 실패: {}", action, e.getClass().getSimpleName());
			throw new PortOneException(Kind.UNKNOWN, 0, null);
		}
	}

	/** 결제대행사 거절(PG_PROVIDER)은 502로 오지만 결제가 일어나지 않은 확정 실패다. 그 밖의 5xx는 결과를 알 수 없다 */
	private static Kind kind(int status, String type) {
		if (status == 404) return Kind.NOT_FOUND;
		if (status >= 400 && status < 500) return Kind.REJECTED;
		if ("PG_PROVIDER".equals(type)) return Kind.REJECTED;
		return Kind.UNKNOWN;
	}

	private static String match(Pattern pattern, String body) {
		if (body == null) return null;
		Matcher matcher = pattern.matcher(body);
		return matcher.find() ? matcher.group(1) : null;
	}

	private PortOneException unexpected(String action) {
		log.warn("포트원 {} 응답에 필요한 값이 없습니다.", action);
		return new PortOneException(Kind.UNKNOWN, 200, null);
	}

	/** 카드는 "카드 이름 마스킹 번호", 간편결제는 "카카오페이" 또는 "카카오페이 · 카드 이름" */
	static String display(JsonNode methods) {
		for (JsonNode method : methods) {
			String type = method.path("type").asString("");
			if (type.endsWith("Card")) return cardLabel(method.path("card"), "카드");
			if (type.endsWith("EasyPay")) {
				String provider = "KAKAOPAY".equals(method.path("provider").asString("")) ? "카카오페이" : method.path("provider").asString("간편결제");
				JsonNode inner = method.path("method");
				if (inner.path("type").asString("").endsWith("Card")) return provider + " · " + cardLabel(inner.path("card"), "카드");
				return provider;
			}
		}
		return "";
	}

	private static String cardLabel(JsonNode card, String fallback) {
		String name = card.path("name").asString("");
		String number = card.path("number").asString("");
		String label = (StringUtils.hasText(name) ? name : fallback) + (StringUtils.hasText(number) ? " " + number : "");
		return label.trim();
	}
}
