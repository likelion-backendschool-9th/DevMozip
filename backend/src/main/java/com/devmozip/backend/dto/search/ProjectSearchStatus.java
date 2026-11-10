package com.devmozip.backend.dto.search;

import java.time.LocalDate;
import com.devmozip.backend.entity.enums.ProjectStatus;

/**
 * 프로젝트 탐색의 모집 상태 5개(목업 projectStatus와 같은 계산).
 * RECRUITMENT_CLOSED는 DB에 저장하지 않는 검색 전용 값이다: status가 RECRUITING이고 모집 마감일이 지났다.
 * projects.status와 ProjectStatus는 바꾸지 않는다.
 */
public enum ProjectSearchStatus {
	RECRUITING,
	RECRUITMENT_CLOSED,
	RECRUITMENT_COMPLETED,
	IN_PROGRESS,
	COMPLETED;

	/** 마감일 당일까지는 모집 중이다. */
	public static ProjectSearchStatus of(ProjectStatus status, LocalDate recruitmentEnd, LocalDate today) {
		return switch (status) {
			case RECRUITING -> recruitmentEnd.isBefore(today) ? RECRUITMENT_CLOSED : RECRUITING;
			case RECRUITMENT_COMPLETED -> RECRUITMENT_COMPLETED;
			case IN_PROGRESS -> IN_PROGRESS;
			case COMPLETED -> COMPLETED;
		};
	}

	static ProjectSearchStatus parse(String value) {
		if (value == null || value.isEmpty()) return null;
		for (ProjectSearchStatus status : values()) {
			if (status.name().equals(value)) return status;
		}
		throw SearchParameters.invalid("모집 상태를 확인해 주세요.");
	}
}
