package com.devmozip.backend.dto.member;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record AccountWithdrawalRequest(
	@Size(max = 256) String currentPassword,
	@Size(max = 2048) String kakaoCode,
	@AssertTrue(message = "탈퇴 시 개인정보 삭제와 환불 불가 안내를 확인해 주세요.") boolean confirmed
) {}
