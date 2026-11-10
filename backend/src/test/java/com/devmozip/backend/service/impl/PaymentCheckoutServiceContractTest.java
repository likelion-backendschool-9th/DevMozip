package com.devmozip.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.service.PaymentCheckoutService;
import com.devmozip.backend.service.PaymentCheckoutService.PendingCheckout;

/** 메모리·Redis 구현이 같은 규칙을 지키는지 확인한다. */
abstract class PaymentCheckoutServiceContractTest {
	protected PaymentProperties properties;
	protected PaymentCheckoutService service;

	protected abstract PaymentCheckoutService createService(PaymentProperties properties);

	/** 수명을 실제로 기다리지 않고 흐르게 한다 */
	protected abstract void advance(Duration duration);

	@BeforeEach
	void setUp() {
		properties = new PaymentProperties();
		service = createService(properties);
	}

	private static PendingCheckout checkout(String orderId) {
		return new PendingCheckout(orderId, 7L, 1L, PaymentProvider.TOSS_PAYMENTS, 9900, "KRW", "customer-" + orderId);
	}

	@Test
	void 저장한_주문을_한_번만_꺼낸다() {
		service.save(checkout("order-1"));
		assertThat(service.consume("order-1")).contains(checkout("order-1"));
		assertThat(service.consume("order-1")).isEmpty();
		assertThat(service.consume("unknown")).isEmpty();
	}

	@Test
	void 수명_15분이_지나면_꺼낼_수_없다() {
		assertThat(properties.getCheckoutTtl()).isEqualTo(Duration.ofMinutes(15));
		service.save(checkout("order-2"));
		service.save(checkout("order-3"));
		advance(Duration.ofMinutes(14));
		assertThat(service.consume("order-2")).isPresent();
		advance(Duration.ofMinutes(2));
		assertThat(service.consume("order-3")).isEmpty();
	}

	@Test
	void 동시에_꺼내도_한_요청만_받는다() throws Exception {
		service.save(checkout("order-4"));
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Optional<PendingCheckout>>> tasks = new ArrayList<>();
			for (int i = 0; i < 8; i++) tasks.add(() -> service.consume("order-4"));
			long found = 0;
			for (Future<Optional<PendingCheckout>> result : pool.invokeAll(tasks)) if (result.get().isPresent()) found++;
			assertThat(found).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}
}
