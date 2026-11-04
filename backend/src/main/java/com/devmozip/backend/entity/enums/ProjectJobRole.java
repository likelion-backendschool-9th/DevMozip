package com.devmozip.backend.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 회원의 직군 허용 값과 분리한 프로젝트 모집 코드. */
@Getter
@RequiredArgsConstructor
public enum ProjectJobRole {
	FRONTEND("프론트엔드"), BACKEND("백엔드"), FULLSTACK("풀스택"),
	DESIGNER("UI/UX"), DEVOPS("DevOps"), DATA_ENGINEER("데이터 엔지니어"),
	AI_ENGINEER("AI 엔지니어"), MOBILE("모바일"), GAME("게임"), OTHER("기타");
	private final String label;
}
