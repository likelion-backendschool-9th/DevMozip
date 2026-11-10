package com.devmozip.backend.service;

import java.util.Optional;

import com.devmozip.backend.dto.payment.CheckoutRequest;
import com.devmozip.backend.dto.payment.CheckoutResponse;
import com.devmozip.backend.dto.payment.ConfirmRequest;
import com.devmozip.backend.dto.payment.PaymentHistoryPageResponse;
import com.devmozip.backend.dto.subscription.PaymentMethodResponse;
import com.devmozip.backend.dto.subscription.SubscriptionResponse;

/** 결제 준비·승인(13.2), 결제 내역(13.5), 결제 수단(17.5). 요청자는 인증 정보의 이메일로만 찾는다 */
public interface PaymentService {

	/** 주문 번호와 구매자 ID를 만들어 회원·상품·금액에 묶어 두고, 포트원 SDK에 넘길 값을 돌려준다 */
	CheckoutResponse checkout(String email, CheckoutRequest request);

	/** 빌링키를 확인하고 첫 달을 결제한 뒤 구독을 시작한다. 역할은 PREMIUM이 된다 */
	SubscriptionResponse confirm(String email, ConfirmRequest request);

	PaymentHistoryPageResponse history(String email, int page, int size);

	Optional<PaymentMethodResponse> paymentMethod(String email);
}
