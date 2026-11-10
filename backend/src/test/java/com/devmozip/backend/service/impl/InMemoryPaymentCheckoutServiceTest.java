package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.service.PaymentCheckoutService;

class InMemoryPaymentCheckoutServiceTest extends PaymentCheckoutServiceContractTest {
	private Instant now;

	@Override
	protected PaymentCheckoutService createService(PaymentProperties properties) {
		now = Instant.parse("2026-11-09T03:00:00Z");
		Clock clock = new Clock() {
			@Override public ZoneId getZone() { return ZoneOffset.UTC; }
			@Override public Clock withZone(ZoneId zone) { return this; }
			@Override public Instant instant() { return now; }
		};
		return new InMemoryPaymentCheckoutService(properties, clock);
	}

	@Override
	protected void advance(Duration duration) {
		now = now.plus(duration);
	}
}
