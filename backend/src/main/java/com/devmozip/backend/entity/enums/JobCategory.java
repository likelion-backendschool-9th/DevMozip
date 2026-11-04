package com.devmozip.backend.entity.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 개발 직군 - 회원가입 선택지와 허용 값의 기준이다. label은 화면에 표시하고 DB에 저장하는 문구다.
@Getter
@RequiredArgsConstructor
public enum JobCategory {
    FRONTEND("프론트엔드 개발자"),
    BACKEND("백엔드 개발자"),
    FULLSTACK("풀스택 개발자"),
    MOBILE("모바일 개발자"),
    AI_ENGINEER("AI 엔지니어"),
    DEVOPS("DevOps 엔지니어"),
    DESIGNER("디자이너");

    private final String label;

    public static List<String> labels() {
        return Arrays.stream(values()).map(JobCategory::getLabel).toList();
    }

    public static boolean contains(String label) {
        return labels().contains(label);
    }
}
