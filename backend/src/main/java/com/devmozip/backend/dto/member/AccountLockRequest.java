package com.devmozip.backend.dto.member;

import com.devmozip.backend.utils.StrictBooleanDeserializer;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * PATCH /api/v1/users/me/account-lock 요청 본문. 원하는 최종 값을 받는다.
 * 문자열 "true"나 숫자 1을 boolean으로 바꾸지 않도록 JSON boolean만 받는다. null과 누락은 @NotNull이 400으로 막는다.
 */
public record AccountLockRequest(
	@NotNull(message = "공개 설정 값을 선택해 주세요.")
	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	Boolean isPrivate
) {}
