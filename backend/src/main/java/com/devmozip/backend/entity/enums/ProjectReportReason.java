package com.devmozip.backend.entity.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter @RequiredArgsConstructor
public enum ProjectReportReason {
	FALSE_INFORMATION("허위 모집 정보"), SPAM("스팸 또는 광고"), INAPPROPRIATE_CONTENT("부적절한 내용"), OTHER("기타");
	private final String label;
	public static boolean contains(String value){return Arrays.stream(values()).anyMatch(reason->reason.label.equals(value));}
}
