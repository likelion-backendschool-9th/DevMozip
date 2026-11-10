package com.devmozip.backend.security.payment;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.enums.PaymentProvider;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Member.paymentMethods(JSON 문자열)와 StoredPaymentMethod를 서로 바꾼다.
 * 저장 형식: {"provider","customerId","billingKey"(암호문),"display","registeredAt"}
 */
@Component
public class PaymentMethodCodec {
	private final ObjectMapper mapper;
	private final BillingKeyCipher cipher;

	public PaymentMethodCodec(ObjectMapper mapper, BillingKeyCipher cipher) {
		this.mapper = mapper;
		this.cipher = cipher;
	}

	public void store(Member member, StoredPaymentMethod method) {
		Map<String, String> value = new LinkedHashMap<>();
		value.put("provider", method.provider().name());
		value.put("customerId", method.customerId());
		value.put("billingKey", cipher.encrypt(method.billingKey()));
		value.put("display", method.display());
		value.put("registeredAt", method.registeredAt().toString());
		member.changePaymentMethods(mapper.writeValueAsString(value));
	}

	public void clear(Member member) {
		member.changePaymentMethods(null);
	}

	/** 복호화한 결제 수단. 없거나 읽을 수 없으면 비어 있다 */
	public Optional<StoredPaymentMethod> read(Member member) {
		JsonNode node = parse(member);
		if (node == null) return Optional.empty();
		return Optional.of(new StoredPaymentMethod(PaymentProvider.valueOf(node.get("provider").asString()), node.get("customerId").asString(),
				cipher.decrypt(node.get("billingKey").asString()), node.get("display").asString(), LocalDateTime.parse(node.get("registeredAt").asString())));
	}

	/** 화면에 보여 줄 정보만 읽는다. 빌링키를 복호화하지 않으므로 암호화 키가 없어도 동작한다 */
	public Optional<Map<String, String>> readDisplay(Member member) {
		JsonNode node = parse(member);
		if (node == null) return Optional.empty();
		return Optional.of(Map.of("provider", node.get("provider").asString(), "display", node.get("display").asString()));
	}

	private JsonNode parse(Member member) {
		String raw = member.getPaymentMethods();
		if (!StringUtils.hasText(raw)) return null;
		try {
			JsonNode node = mapper.readTree(raw);
			if (node == null || !node.isObject()) return null;
			for (String field : new String[] { "provider", "customerId", "billingKey", "display", "registeredAt" }) {
				if (!node.hasNonNull(field)) return null;
			}
			PaymentProvider.valueOf(node.get("provider").asString());
			return node;
		} catch (JacksonException | IllegalArgumentException e) {
			// 이 기능 전에 다른 형식으로 들어간 값은 등록된 결제 수단으로 보지 않는다.
			return null;
		}
	}
}
