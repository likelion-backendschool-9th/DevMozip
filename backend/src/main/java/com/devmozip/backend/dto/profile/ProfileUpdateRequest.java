package com.devmozip.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 기본 정보 수정 계약 초안. 사용자 ID·권한·신뢰 온도·이미지는 수정 대상이 아니다. */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = "[가-힣a-zA-Z0-9_ ]{2,20}", message = "닉네임은 한글·영문·숫자·공백·밑줄로 2~20자를 입력해 주세요.")
        String nickname,

        @NotBlank(message = "사용자 태그를 입력해 주세요.")
        @Pattern(regexp = "(?![mM][eE]$)[a-zA-Z0-9_-]{2,30}", message = "태그는 영문·숫자·밑줄·하이픈으로 2~30자이며 me는 사용할 수 없습니다.")
        String tag,

        @NotNull(message = "직군을 선택해 주세요.")
        @Positive(message = "올바른 직군 ID를 입력해 주세요.")
        Long jobCategoryId,

        @Size(max = 150, message = "한 줄 소개는 150자 이내로 입력해 주세요.")
        String summary,

        @PositiveOrZero(message = "경력은 0개월 이상이어야 합니다.")
        Integer career,

        @Pattern(regexp = "AVAILABLE|RESTING|BUSY|OPEN_TO_OFFERS|UNAVAILABLE", message = "올바른 협업 가능 상태를 선택해 주세요.")
        String collaborationStatus,

        @Size(max = 100, message = "연락 가능 시간은 100자 이내로 입력해 주세요.")
        String contactHours
) {
    public ProfileUpdateRequest {
        nickname = nickname == null ? null : nickname.strip();
        tag = tag == null ? null : tag.strip();
        summary = summary == null ? null : summary.strip();
        contactHours = contactHours == null ? null : contactHours.strip();
    }
}
