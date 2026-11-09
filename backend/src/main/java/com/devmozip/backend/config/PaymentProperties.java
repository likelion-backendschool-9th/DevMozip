package com.devmozip.backend.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.devmozip.backend.entity.enums.PaymentProvider;

/**
 * 포트원 결제 설정 (docs/premium-payment-plan.md 4절). 값이 없어도 서버는 뜨고,
 * 설정이 없는 결제 수단의 결제 준비·승인만 503으로 응답한다.
 * 상점 ID·채널 키는 브라우저 SDK에 쓰는 공개 값이고, API Secret과 암호화 키는 백엔드에만 둔다.
 */
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
	/** AES-256 키 길이 */
	private static final int ENCRYPTION_KEY_BYTES = 32;

	private String storeId;
	private String apiSecret;
	private String tossChannelKey;
	private String kakaopayChannelKey;
	private String apiBaseUrl = "https://api.portone.io";
	private String encryptionKey;
	private String domainName;
	private Duration checkoutTtl = Duration.ofMinutes(15);
	private boolean renewalEnabled = true;

	/** 포트원 서버 API를 부를 수 있는지 (결제·삭제·취소에 필요) */
	public boolean isApiConfigured() {
		return StringUtils.hasText(storeId) && StringUtils.hasText(apiSecret) && encryptionKeyBytes() != null;
	}

	/** 이 결제 수단으로 새 결제를 시작할 수 있는지 */
	public boolean isConfigured(PaymentProvider provider) {
		return isApiConfigured() && StringUtils.hasText(channelKey(provider)) && hasDomain();
	}

	public String channelKey(PaymentProvider provider) {
		return provider == PaymentProvider.TOSS_PAYMENTS ? tossChannelKey : kakaopayChannelKey;
	}

	/** 모바일 결제창이 돌아올 BFF 주소. 요청의 Host 헤더가 아니라 설정한 서비스 주소로 만든다 */
	public String callbackUrl(String orderId) {
		return domainName.replaceAll("/+$", "") + "/api/payments/callback?orderId=" + URLEncoder.encode(orderId, StandardCharsets.UTF_8);
	}

	/** base64로 넣은 32바이트 키. 형식이 맞지 않으면 null */
	public byte[] encryptionKeyBytes() {
		if (!StringUtils.hasText(encryptionKey)) return null;
		try {
			byte[] key = Base64.getDecoder().decode(encryptionKey.trim());
			return key.length == ENCRYPTION_KEY_BYTES ? key : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private boolean hasDomain() {
		return domainName != null && (domainName.startsWith("http://") || domainName.startsWith("https://"));
	}

	// Getter, Setter
	public String getStoreId() { return storeId; }
	public void setStoreId(String storeId) { this.storeId = storeId; }
	public String getApiSecret() { return apiSecret; }
	public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
	public String getTossChannelKey() { return tossChannelKey; }
	public void setTossChannelKey(String tossChannelKey) { this.tossChannelKey = tossChannelKey; }
	public String getKakaopayChannelKey() { return kakaopayChannelKey; }
	public void setKakaopayChannelKey(String kakaopayChannelKey) { this.kakaopayChannelKey = kakaopayChannelKey; }
	public String getApiBaseUrl() { return apiBaseUrl; }
	public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
	public String getEncryptionKey() { return encryptionKey; }
	public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
	public String getDomainName() { return domainName; }
	public void setDomainName(String domainName) { this.domainName = domainName; }
	public Duration getCheckoutTtl() { return checkoutTtl; }
	public void setCheckoutTtl(Duration checkoutTtl) { this.checkoutTtl = checkoutTtl; }
	public boolean isRenewalEnabled() { return renewalEnabled; }
	public void setRenewalEnabled(boolean renewalEnabled) { this.renewalEnabled = renewalEnabled; }
}
