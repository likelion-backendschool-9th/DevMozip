package com.devmozip.backend.service.impl;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.service.PaymentCheckoutService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 서버용 결제 대기 정보 저장소. 키마다 수명을 두어 Redis가 스스로 지우고, GETDEL로 한 번만 꺼낸다. */
@Service
@Profile("!local")
public class RedisPaymentCheckoutService implements PaymentCheckoutService {
	private static final String PREFIX = "payment-checkout:";

	private final StringRedisTemplate redis;
	private final PaymentProperties properties;
	private final ObjectMapper mapper;

	public RedisPaymentCheckoutService(StringRedisTemplate redis, PaymentProperties properties, ObjectMapper mapper) {
		this.redis = redis;
		this.properties = properties;
		this.mapper = mapper;
	}

	@Override
	public void save(PendingCheckout checkout) {
		redis.opsForValue().set(PREFIX + checkout.orderId(), mapper.writeValueAsString(checkout), properties.getCheckoutTtl());
	}

	@Override
	public Optional<PendingCheckout> consume(String orderId) {
		String value = redis.opsForValue().getAndDelete(PREFIX + orderId);
		if (value == null) return Optional.empty();
		try {
			return Optional.of(mapper.readValue(value, PendingCheckout.class));
		} catch (JacksonException e) {
			return Optional.empty();
		}
	}
}
