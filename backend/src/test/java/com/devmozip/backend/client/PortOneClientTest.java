package com.devmozip.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.devmozip.backend.client.PortOneException.Kind;
import com.devmozip.backend.config.PaymentProperties;

@ExtendWith(OutputCaptureExtension.class)
class PortOneClientTest {
	private static final String BASE = "https://api.portone.test";
	private static final String SECRET = "portone-secret-value";
	private static final String BILLING_KEY = "billing-key-0123456789";

	private MockRestServiceServer server;
	private PortOneClient client;

	@BeforeEach
	void setUp() {
		PaymentProperties properties = new PaymentProperties();
		properties.setApiBaseUrl(BASE);
		properties.setStoreId("store-test");
		properties.setApiSecret(SECRET);
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new PortOneClient(properties, builder);
	}

	private static String error(String type) {
		return "{\"type\":\"" + type + "\",\"message\":\"사유 " + BILLING_KEY + "\",\"pgCode\":\"CARD_LIMIT\",\"pgMessage\":\"한도 초과\"}";
	}

	@Test
	void 빌링키를_조회해_상태_채널_발급_주문_번호와_카드_표시_정보를_읽는다() {
		server.expect(requestTo(BASE + "/billing-keys/" + BILLING_KEY + "?storeId=store-test"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "PortOne " + SECRET))
				.andRespond(withSuccess("""
						{"status":"ISSUED","billingKey":"%s","issueId":"order-1","channels":[{"type":"LIVE","key":"channel-key-toss","pgProvider":"TOSSPAYMENTS","pgMerchantId":"m"}],
						 "methods":[{"type":"BillingKeyPaymentMethodCard","card":{"name":"신한카드","number":"12345678****123*"}}],"customer":{"id":"customer-1"}}
						""".formatted(BILLING_KEY), MediaType.APPLICATION_JSON));
		PortOneClient.BillingKeyInfo info = client.getBillingKey(BILLING_KEY);
		assertThat(info.issued()).isTrue();
		assertThat(info.issueId()).isEqualTo("order-1");
		assertThat(info.channelKeys()).containsExactly("channel-key-toss");
		assertThat(info.display()).isEqualTo("신한카드 12345678****123*");
		server.verify();
	}

	@Test
	void 카카오페이_빌링키는_간편결제_이름을_표시한다() {
		server.expect(requestTo(BASE + "/billing-keys/" + BILLING_KEY + "?storeId=store-test"))
				.andRespond(withSuccess("""
						{"status":"ISSUED","issueId":"order-2","channels":[{"key":"channel-key-kakao"}],
						 "methods":[{"type":"BillingKeyPaymentMethodEasyPay","provider":"KAKAOPAY","method":{"type":"BillingKeyPaymentMethodCard","card":{"name":"국민카드"}}}]}
						""", MediaType.APPLICATION_JSON));
		assertThat(client.getBillingKey(BILLING_KEY).display()).isEqualTo("카카오페이 · 국민카드");
	}

	@Test
	void 빌링키_결제는_주문_번호를_경로에_넣고_서버가_정한_금액을_보낸다() {
		server.expect(requestTo(BASE + "/payments/order-1/billing-key"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "PortOne " + SECRET))
				.andExpect(content().json("""
						{"storeId":"store-test","billingKey":"%s","orderName":"DevMozip 프리미엄 월 구독","customer":{"id":"customer-1"},"amount":{"total":9900},"currency":"KRW"}
						""".formatted(BILLING_KEY), org.springframework.test.json.JsonCompareMode.STRICT))
				.andRespond(withSuccess("{\"payment\":{\"pgTxId\":\"pg-tx-1\",\"paidAt\":\"2026-11-09T03:00:00Z\"}}", MediaType.APPLICATION_JSON));
		PortOneClient.PaidPayment paid = client.payWithBillingKey("order-1", BILLING_KEY, "DevMozip 프리미엄 월 구독", "customer-1", 9900, "KRW");
		assertThat(paid.pgTxId()).isEqualTo("pg-tx-1");
		assertThat(paid.paidAt()).isEqualTo("2026-11-09T03:00:00Z");
	}

	@Test
	void 결제대행사_거절은_확정_실패로_바꾸고_로그에는_유형과_코드만_남긴다(CapturedOutput output) {
		server.expect(requestTo(BASE + "/payments/order-1/billing-key"))
				.andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body(error("PG_PROVIDER")));
		assertThatThrownBy(() -> client.payWithBillingKey("order-1", BILLING_KEY, "n", "c", 9900, "KRW"))
				.isInstanceOfSatisfying(PortOneException.class, e -> {
					assertThat(e.getKind()).isEqualTo(Kind.REJECTED);
					assertThat(e.getType()).isEqualTo("PG_PROVIDER");
					assertThat(e.getMessage()).doesNotContain(BILLING_KEY);
				});
		assertThat(output).contains("type=PG_PROVIDER", "pgCode=CARD_LIMIT").doesNotContain(BILLING_KEY).doesNotContain(SECRET);
	}

	@Test
	void 응답이_없거나_서버_오류면_결과를_알_수_없는_것으로_본다() {
		server.expect(requestTo(BASE + "/payments/order-1/billing-key")).andRespond(withException(new SocketTimeoutException("timeout")));
		assertThatThrownBy(() -> client.payWithBillingKey("order-1", BILLING_KEY, "n", "c", 9900, "KRW"))
				.isInstanceOfSatisfying(PortOneException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.UNKNOWN));
		server.reset();
		server.expect(requestTo(BASE + "/payments/order-2/billing-key"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{}"));
		assertThatThrownBy(() -> client.payWithBillingKey("order-2", BILLING_KEY, "n", "c", 9900, "KRW"))
				.isInstanceOfSatisfying(PortOneException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.UNKNOWN));
	}

	@Test
	void 같은_주문_번호로_다시_결제하면_거절된다() {
		server.expect(requestTo(BASE + "/payments/order-1/billing-key"))
				.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(error("ALREADY_PAID")));
		assertThatThrownBy(() -> client.payWithBillingKey("order-1", BILLING_KEY, "n", "c", 9900, "KRW"))
				.isInstanceOfSatisfying(PortOneException.class, e -> assertThat(e).extracting(PortOneException::getKind, PortOneException::getType)
						.containsExactly(Kind.REJECTED, "ALREADY_PAID"));
	}

	@Test
	void 빌링키_삭제는_이미_삭제됐거나_없어도_성공으로_본다() {
		server.expect(requestTo(BASE + "/billing-keys/" + BILLING_KEY + "?storeId=store-test")).andExpect(method(HttpMethod.DELETE))
				.andRespond(withSuccess("{\"deletedAt\":\"2026-11-09T03:00:00Z\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/billing-keys/" + BILLING_KEY + "?storeId=store-test"))
				.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(error("BILLING_KEY_ALREADY_DELETED")));
		server.expect(requestTo(BASE + "/billing-keys/" + BILLING_KEY + "?storeId=store-test"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("BILLING_KEY_NOT_FOUND")));
		server.expect(requestTo(BASE + "/billing-keys/" + BILLING_KEY + "?storeId=store-test"))
				.andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"CHANNEL_SPECIFIC\"}"));
		client.deleteBillingKey(BILLING_KEY);
		client.deleteBillingKey(BILLING_KEY);
		client.deleteBillingKey(BILLING_KEY);
		assertThatThrownBy(() -> client.deleteBillingKey(BILLING_KEY)).isInstanceOf(PortOneException.class);
	}

	@Test
	void 결제_취소는_사유를_넣고_결제_조회는_상태를_돌려준다() throws IOException {
		server.expect(requestTo(BASE + "/payments/order-1/cancel")).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"storeId\":\"store-test\",\"reason\":\"구독 저장 실패\"}"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/payments/order-1?storeId=store-test"))
				.andRespond(withSuccess("{\"status\":\"CANCELLED\"}", MediaType.APPLICATION_JSON));
		client.cancelPayment("order-1", "구독 저장 실패");
		assertThat(client.getPaymentStatus("order-1")).isEqualTo("CANCELLED");
		server.verify();
	}

	@Test
	void 없는_빌링키_조회는_NOT_FOUND다() {
		server.expect(requestTo(BASE + "/billing-keys/missing?storeId=store-test"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("BILLING_KEY_NOT_FOUND")));
		assertThatThrownBy(() -> client.getBillingKey("missing"))
				.isInstanceOfSatisfying(PortOneException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.NOT_FOUND));
	}
}
