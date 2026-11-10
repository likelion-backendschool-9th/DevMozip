package com.devmozip.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.subscription.PaymentMethodResponse;
import com.devmozip.backend.service.PaymentService;

/** WBS 17.5 결제 수단 조회. 등록된 결제 수단이 없으면 204 */
@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {
	private final PaymentService service;

	public PaymentMethodController(PaymentService service) {
		this.service = service;
	}

	@GetMapping("/me")
	public ResponseEntity<PaymentMethodResponse> me(Authentication authentication) {
		return service.paymentMethod(authentication.getName()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}
}
