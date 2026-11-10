package com.devmozip.backend.controller;

import com.devmozip.backend.dto.member.AccountWithdrawalRequest;
import com.devmozip.backend.service.AccountWithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountWithdrawalController {
	private final AccountWithdrawalService service;
	public AccountWithdrawalController(AccountWithdrawalService service) { this.service = service; }

	@DeleteMapping("/api/v1/users/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(Authentication authentication, @Valid @RequestBody AccountWithdrawalRequest request) {
		service.withdraw(authentication.getName(), request);
	}
}
