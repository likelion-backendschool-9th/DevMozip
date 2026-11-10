package com.devmozip.backend.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.subscription.SubscriptionPlanResponse;
import com.devmozip.backend.dto.subscription.SubscriptionResponse;
import com.devmozip.backend.service.SubscriptionService;

/** WBS 13.1 상품 안내(공개), 13.3 구독 상태, 13.4 구독 해지 */
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {
	private final SubscriptionService service;

	public SubscriptionController(SubscriptionService service) {
		this.service = service;
	}

	@GetMapping("/plans")
	public List<SubscriptionPlanResponse> plans() {
		return service.plans();
	}

	@GetMapping("/me")
	public SubscriptionResponse me(Authentication authentication) {
		return service.me(authentication.getName());
	}

	@PostMapping("/cancel")
	public SubscriptionResponse cancel(Authentication authentication) {
		return service.cancel(authentication.getName());
	}
}
