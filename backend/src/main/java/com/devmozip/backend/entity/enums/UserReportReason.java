package com.devmozip.backend.entity.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 사용자 신고 사유 - 허용 값의 기준이다. label을 reports.reason에 저장한다.
@Getter
@RequiredArgsConstructor
public enum UserReportReason {
	SPAM("스팸 또는 광고"),
	INAPPROPRIATE_MESSAGE("부적절한 메시지"),
	ABUSE("욕설 또는 괴롭힘"),
	IMPERSONATION("사칭"),
	OTHER("기타");

	private final String label;

	public static boolean contains(String label) {
		return Arrays.stream(values()).anyMatch(reason -> reason.label.equals(label));
	}
}
