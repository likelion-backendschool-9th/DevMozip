package com.devmozip.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.devmozip.backend.entity.enums.PaymentProvider;

class PaymentPropertiesTest {

	private static PaymentProperties complete() {
		PaymentProperties properties = new PaymentProperties();
		properties.setStoreId("store-test");
		properties.setApiSecret("secret");
		properties.setTossChannelKey("channel-key-toss");
		properties.setKakaopayChannelKey("channel-key-kakao");
		properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
		properties.setDomainName("https://devmozip.test/");
		return properties;
	}

	@Test
	void 모든_값이_있으면_두_결제_수단을_쓸_수_있다() {
		PaymentProperties properties = complete();
		assertThat(properties.isConfigured(PaymentProvider.TOSS_PAYMENTS)).isTrue();
		assertThat(properties.isConfigured(PaymentProvider.KAKAO_PAY)).isTrue();
		assertThat(properties.channelKey(PaymentProvider.KAKAO_PAY)).isEqualTo("channel-key-kakao");
	}

	@Test
	void 채널_키가_없는_결제_수단만_막는다() {
		PaymentProperties properties = complete();
		properties.setKakaopayChannelKey("");
		assertThat(properties.isConfigured(PaymentProvider.TOSS_PAYMENTS)).isTrue();
		assertThat(properties.isConfigured(PaymentProvider.KAKAO_PAY)).isFalse();
	}

	@Test
	void API_Secret_암호화_키_서비스_주소_중_하나라도_없으면_결제하지_않는다() {
		PaymentProperties noSecret = complete();
		noSecret.setApiSecret(null);
		assertThat(noSecret.isApiConfigured()).isFalse();
		PaymentProperties shortKey = complete();
		shortKey.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));
		assertThat(shortKey.isApiConfigured()).isFalse();
		PaymentProperties badDomain = complete();
		badDomain.setDomainName("devmozip.test");
		assertThat(badDomain.isApiConfigured()).isTrue();
		assertThat(badDomain.isConfigured(PaymentProvider.TOSS_PAYMENTS)).isFalse();
	}

	@Test
	void 결과_주소는_설정한_서비스_주소와_BFF_경로로_만든다() {
		assertThat(complete().callbackUrl("a b&c")).isEqualTo("https://devmozip.test/api/payments/callback?orderId=a+b%26c");
	}
}
