package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.service.PaymentCheckoutService;

/** local 프로필용 저장소. Redis 없이 실행하며 서버를 재시작하면 사라진다. */
@Service
@Profile("local")
public class InMemoryPaymentCheckoutService implements PaymentCheckoutService {
	private record Entry(PendingCheckout checkout, Instant expiresAt) {
	}

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private final PaymentProperties properties;
	private final Clock clock;

	@Autowired
	public InMemoryPaymentCheckoutService(PaymentProperties properties) {
		this(properties, Clock.systemUTC());
	}

	InMemoryPaymentCheckoutService(PaymentProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void save(PendingCheckout checkout) {
		Instant now = clock.instant();
		// 만료된 값이 쌓이지 않도록 저장할 때 함께 지운다.
		entries.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
		entries.put(checkout.orderId(), new Entry(checkout, now.plus(properties.getCheckoutTtl())));
	}

	@Override
	public Optional<PendingCheckout> consume(String orderId) {
		Entry entry = entries.remove(orderId);
		if (entry == null || !entry.expiresAt().isAfter(clock.instant())) return Optional.empty();
		return Optional.of(entry.checkout());
	}
}
