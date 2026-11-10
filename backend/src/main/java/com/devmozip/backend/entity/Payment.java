package com.devmozip.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.entity.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 내역 (ERD payments). order_id·created_at은 2026-11-09 결정으로 ERD에 추가한 컬럼이다.
 * order_id는 포트원 paymentId로 쓰며, 포트원은 같은 paymentId의 두 번째 결제를 거절한다.
 */
@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subscription_id", nullable = false)
	private Subscription subscription;

	@Column(name = "order_id", nullable = false, length = 64)
	private String orderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private PaymentProvider provider;

	/** 포트원 결제 응답의 PG 거래 번호(pgTxId). 응답을 받기 전에는 비어 있다 */
	@Column(name = "provider_payment_id", length = 255)
	private String providerPaymentId;

	/** 결제 당시 표시용 결제 수단 정보. 빌링키는 넣지 않는다 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payment_method_snapshot")
	private Map<String, String> paymentMethodSnapshot;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3, columnDefinition = "char(3)")
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** 첫 결제: 포트원 승인이 끝난 뒤 한 번에 저장한다 */
	public static Payment paid(Subscription subscription, String orderId, PaymentProvider provider, Map<String, String> snapshot,
			BigDecimal amount, String currency, String providerPaymentId, LocalDateTime paidAt, LocalDateTime createdAt) {
		Payment payment = pending(subscription, orderId, provider, snapshot, amount, currency, createdAt);
		payment.markPaid(providerPaymentId, paidAt);
		return payment;
	}

	/** 매월 결제: 포트원에 요청하기 전에 먼저 저장해 두 번 결제하지 않게 한다 */
	public static Payment pending(Subscription subscription, String orderId, PaymentProvider provider, Map<String, String> snapshot,
			BigDecimal amount, String currency, LocalDateTime createdAt) {
		Payment payment = new Payment();
		payment.subscription = subscription;
		payment.orderId = orderId;
		payment.provider = provider;
		payment.paymentMethodSnapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
		payment.amount = amount;
		payment.currency = currency;
		payment.status = PaymentStatus.PENDING;
		payment.createdAt = createdAt;
		return payment;
	}

	public void markPaid(String providerPaymentId, LocalDateTime paidAt) {
		this.status = PaymentStatus.PAID;
		this.providerPaymentId = providerPaymentId;
		this.paidAt = paidAt;
	}

	public void markFailed() {
		this.status = PaymentStatus.FAILED;
	}

	public void markCanceled() {
		this.status = PaymentStatus.CANCELED;
	}
}
