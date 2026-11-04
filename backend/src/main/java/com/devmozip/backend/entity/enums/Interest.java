package com.devmozip.backend.entity.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 관심 분야 - 회원가입 선택지와 허용 값의 기준이다. label은 화면에 표시하고 DB에 저장하는 문구다.
@Getter
@RequiredArgsConstructor
public enum Interest {
    PRODUCTIVITY("생산성"),
    FINTECH("핀테크"),
    EDUCATION("교육"),
    COMMUNITY("커뮤니티"),
    AI("AI");

    private final String label;

    public static List<String> labels() {
        return Arrays.stream(values()).map(Interest::getLabel).toList();
    }

    public static boolean contains(String label) {
        return labels().contains(label);
    }
}
