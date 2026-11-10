package com.devmozip.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.payment.CheckoutRequest;
import com.devmozip.backend.dto.payment.CheckoutResponse;
import com.devmozip.backend.dto.payment.ConfirmRequest;
import com.devmozip.backend.dto.payment.PaymentHistoryPageResponse;
import com.devmozip.backend.dto.subscription.SubscriptionResponse;
import com.devmozip.backend.service.PaymentService;

import jakarta.validation.Valid;

/** WBS 13.2 결제 준비·승인(결제 준비는 WBS 외 추가), 13.5 결제 내역 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
	private final PaymentService service;

	public PaymentController(PaymentService service) {
		this.service = service;
	}

	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public CheckoutResponse checkout(Authentication authentication, @Valid @RequestBody CheckoutRequest request) {
		return service.checkout(authentication.getName(), request);
	}

	@PostMapping("/confirm")
	public SubscriptionResponse confirm(Authentication authentication, @Valid @RequestBody ConfirmRequest request) {
		return service.confirm(authentication.getName(), request);
	}

	@GetMapping("/me")
	public PaymentHistoryPageResponse history(Authentication authentication, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.history(authentication.getName(), page, size);
	}
}
