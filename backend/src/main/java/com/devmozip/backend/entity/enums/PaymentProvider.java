package com.devmozip.backend.entity.enums;

/**
 * 포트원 채널로 연결한 결제 수단. 저장 값은 포트원이 아니라 실제 결제대행사를 뜻한다.
 * billingKeyMethod는 포트원 브라우저 SDK requestIssueBillingKey()의 값이다.
 */
public enum PaymentProvider {
	TOSS_PAYMENTS("CARD", "토스페이먼츠"),
	KAKAO_PAY("EASY_PAY", "카카오페이");

	private final String billingKeyMethod;
	private final String label;

	PaymentProvider(String billingKeyMethod, String label) {
		this.billingKeyMethod = billingKeyMethod;
		this.label = label;
	}

	public String getBillingKeyMethod() {
		return billingKeyMethod;
	}

	public String getLabel() {
		return label;
	}
}
