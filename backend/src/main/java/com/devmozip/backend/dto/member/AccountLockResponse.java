package com.devmozip.backend.dto.member;

/** GET·PATCH /api/v1/users/me/account-lock 응답 본문. 필드 이름은 isPrivate다(MemberAccountLockIntegrationTest가 고정한다). */
public record AccountLockResponse(boolean isPrivate) {}
