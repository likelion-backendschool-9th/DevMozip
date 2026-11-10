package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.Sanction;

/**
 * 회원의 최근 제재 (WBS 14.2·14.3). type은 WARNED·SUSPENDED다. revokedAt이 있으면 해제(철회)된 제재다.
 * endsAt은 기한 있는 제재가 없어 항상 null이다.
 */
public record AdminSanctionResponse(String type, String reason, String adminNickname, LocalDateTime startsAt, LocalDateTime endsAt,
	LocalDateTime revokedAt) {

	public static AdminSanctionResponse of(Sanction sanction) {
		return sanction == null ? null : new AdminSanctionResponse(sanction.getType().name(), sanction.getReason(),
			sanction.getAdmin().getNickname(), sanction.getStartsAt(), sanction.getEndsAt(), sanction.getRevokedAt());
	}
}
