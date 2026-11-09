package com.devmozip.backend.entity;

import java.math.BigDecimal;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프리미엄 상품 (ERD subscription_plans). 무료 요금제는 결제 상품이 아니므로 행으로 두지 않는다. */
@Entity
@Table(name = "subscription_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan {
	/** 월 결제 주기. 현재 상품은 이것 하나다 */
	public static final String MONTHLY = "MONTHLY";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	@Column(nullable = false, length = 3, columnDefinition = "char(3)")
	private String currency;

	@Column(name = "billing_cycle", nullable = false, length = 20)
	private String billingCycle;

	/** 기능별 한도. 키 이름은 한도를 쓰는 기능 담당과 맞춘다 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "feature_limits")
	private Map<String, Integer> featureLimits;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	public static SubscriptionPlan monthly(String name, BigDecimal price, String currency, Map<String, Integer> featureLimits) {
		SubscriptionPlan plan = new SubscriptionPlan();
		plan.name = name;
		plan.price = price;
		plan.currency = currency;
		plan.billingCycle = MONTHLY;
		plan.featureLimits = Map.copyOf(featureLimits);
		plan.active = true;
		return plan;
	}

	/** 포트원에 보내는 원 단위 금액. 소수점이 있는 가격은 결제할 수 없다 */
	public long amountInWon() {
		return price.longValueExact();
	}
}
