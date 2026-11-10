package com.devmozip.backend.security.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.devmozip.backend.config.PaymentProperties;

class BillingKeyCipherTest {

	static String randomKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	static BillingKeyCipher cipher(String key) {
		PaymentProperties properties = new PaymentProperties();
		properties.setEncryptionKey(key);
		return new BillingKeyCipher(properties);
	}

	@Test
	void 암호화한_값을_같은_키로_되돌린다() {
		BillingKeyCipher cipher = cipher(randomKey());
		String stored = cipher.encrypt("billing-key-abc123");
		assertThat(stored).startsWith("v1:").doesNotContain("billing-key-abc123");
		assertThat(cipher.decrypt(stored)).isEqualTo("billing-key-abc123");
	}

	@Test
	void 같은_빌링키도_매번_다른_암호문이_된다() {
		BillingKeyCipher cipher = cipher(randomKey());
		assertThat(cipher.encrypt("billing-key-abc123")).isNotEqualTo(cipher.encrypt("billing-key-abc123"));
	}

	@Test
	void 다른_키나_손상된_값은_복호화하지_않는다() {
		String stored = cipher(randomKey()).encrypt("billing-key-abc123");
		assertThatThrownBy(() -> cipher(randomKey()).decrypt(stored)).isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("billing-key");
		BillingKeyCipher cipher = cipher(randomKey());
		assertThatThrownBy(() -> cipher.decrypt("v1:not-base64!")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cipher.decrypt("plain")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 키가_없거나_32바이트가_아니면_쓰지_않는다() {
		assertThatThrownBy(() -> cipher(null).encrypt("x")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[16])).encrypt("x")).isInstanceOf(IllegalStateException.class);
	}
}
