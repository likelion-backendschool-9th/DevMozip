package com.devmozip.backend.entity.enums;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 게시글·댓글 신고 사유 - 허용 값의 기준이다. label을 reports.reason에 저장한다 (목업 socialReportReasons와 같다).
@Getter
@RequiredArgsConstructor
public enum PostReportReason {
	SPAM("스팸 또는 광고"),
	ABUSE("욕설 또는 혐오 표현"),
	FALSE_INFORMATION("허위 정보"),
	COPYRIGHT("저작권 침해"),
	OTHER("기타");

	private final String label;

	public static boolean contains(String label) {
		return Arrays.stream(values()).anyMatch(reason -> reason.label.equals(label));
	}
}
