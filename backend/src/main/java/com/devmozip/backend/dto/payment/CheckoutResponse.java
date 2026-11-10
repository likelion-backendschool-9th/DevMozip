package com.devmozip.backend.dto.payment;

/**
 * 13.2 결제 준비 응답. 화면은 이 값으로 포트원 SDK requestIssueBillingKey()를 부른다.
 * orderId는 SDK의 issueId, customerId는 customer.customerId로 넘긴다. storeId·channelKey는 공개 값이다.
 */
public record CheckoutResponse(String orderId, String provider, String storeId, String channelKey, String billingKeyMethod,
		String issueName, String customerId, String redirectUrl) {
}
