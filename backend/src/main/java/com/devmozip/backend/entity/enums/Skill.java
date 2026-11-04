package com.devmozip.backend.entity.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 기술 스택 - 회원가입 선택지와 허용 값의 기준이다. label은 화면에 표시하고 DB에 저장하는 문구다.
@Getter
@RequiredArgsConstructor
public enum Skill {
    REACT("React"),
    TYPESCRIPT("TypeScript"),
    JAVA("Java"),
    SPRING_BOOT("Spring Boot"),
    PYTHON("Python"),
    NODE_JS("Node.js"),
    FIGMA("Figma");

    private final String label;

    public static List<String> labels() {
        return Arrays.stream(values()).map(Skill::getLabel).toList();
    }

    public static boolean contains(String label) {
        return labels().contains(label);
    }
}
