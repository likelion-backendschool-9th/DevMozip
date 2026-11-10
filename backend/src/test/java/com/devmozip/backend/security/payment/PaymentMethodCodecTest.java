package com.devmozip.backend.security.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.enums.PaymentProvider;

import tools.jackson.databind.json.JsonMapper;

class PaymentMethodCodecTest {
	private final PaymentMethodCodec codec = new PaymentMethodCodec(JsonMapper.builder().build(), BillingKeyCipherTest.cipher(BillingKeyCipherTest.randomKey()));

	private static Member member() {
		return Member.builder().email("payer@devmozip.test").role(Member.MemberRole.USER).build();
	}

	@Test
	void 저장한_결제_수단을_그대로_읽고_DB_값에는_빌링키_원문이_없다() {
		Member member = member();
		LocalDateTime registeredAt = LocalDateTime.of(2026, 11, 9, 12, 0);
		codec.store(member, new StoredPaymentMethod(PaymentProvider.KAKAO_PAY, "customer-1", "billing-key-plain", "카카오페이", registeredAt));
		assertThat(member.getPaymentMethods()).doesNotContain("billing-key-plain").contains("\"provider\":\"KAKAO_PAY\"");
		StoredPaymentMethod read = codec.read(member).orElseThrow();
		assertThat(read.billingKey()).isEqualTo("billing-key-plain");
		assertThat(read.customerId()).isEqualTo("customer-1");
		assertThat(read.registeredAt()).isEqualTo(registeredAt);
		assertThat(read.toString()).doesNotContain("billing-key-plain");
		assertThat(codec.readDisplay(member)).contains(Map.of("provider", "KAKAO_PAY", "display", "카카오페이"));
	}

	@Test
	void 비우거나_다른_형식의_값은_결제_수단이_없는_것으로_본다() {
		Member member = member();
		assertThat(codec.read(member)).isEmpty();
		member.changePaymentMethods("[{\"card\":\"demo\"}]");
		assertThat(codec.read(member)).isEmpty();
		assertThat(codec.readDisplay(member)).isEmpty();
		member.changePaymentMethods("{\"provider\":\"PAYPAL\",\"customerId\":\"c\",\"billingKey\":\"v1:x\",\"display\":\"d\",\"registeredAt\":\"2026-11-09T00:00\"}");
		assertThat(codec.read(member)).isEmpty();
		codec.store(member, new StoredPaymentMethod(PaymentProvider.TOSS_PAYMENTS, "c", "b", "카드", LocalDateTime.now()));
		codec.clear(member);
		assertThat(member.getPaymentMethods()).isNull();
	}
}
