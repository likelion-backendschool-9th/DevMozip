package com.devmozip.backend.security.payment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.devmozip.backend.config.PaymentProperties;

/**
 * 포트원 빌링키를 DB에 저장하기 전에 AES-256-GCM으로 암호화한다.
 * DB 백업·덤프에 빌링키 원문이 남지 않게 하기 위해서다. 키는 PAYMENT_ENCRYPTION_KEY(base64 32바이트)다.
 * 저장 형식: "v1:" + base64(IV 12바이트 + 암호문과 인증 태그)
 */
@Component
public class BillingKeyCipher {
	private static final String PREFIX = "v1:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final PaymentProperties properties;
	private final SecureRandom random = new SecureRandom();

	public BillingKeyCipher(PaymentProperties properties) {
		this.properties = properties;
	}

	public String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("빌링키를 암호화하지 못했습니다.", e);
		}
	}

	public String decrypt(String stored) {
		if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalStateException("저장된 빌링키 형식이 올바르지 않습니다.");
		try {
			byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			if (data.length <= IV_BYTES) throw new IllegalStateException("저장된 빌링키 형식이 올바르지 않습니다.");
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// 키가 바뀌었거나 값이 손상됐다. 원인 메시지에 값이 섞이지 않도록 감싸지 않는다.
			throw new IllegalStateException("저장된 빌링키를 복호화하지 못했습니다.");
		}
	}

	private SecretKeySpec key() {
		byte[] key = properties.encryptionKeyBytes();
		if (key == null) throw new IllegalStateException("PAYMENT_ENCRYPTION_KEY가 설정되지 않았습니다.");
		return new SecretKeySpec(key, "AES");
	}
}
