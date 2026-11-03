package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileUpdateRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 인증 및 저장 로직과 분리한 입력 검증. 실제 회원 수정 Service에 통합할 준비 단계다. */
@Component
@RequiredArgsConstructor
public class ProfileUpdateValidator {
    private final Validator validator;

    public ProfileUpdateRequest validate(ProfileUpdateRequest request) {
        Objects.requireNonNull(request, "프로필 수정 요청이 필요합니다.");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }
}
