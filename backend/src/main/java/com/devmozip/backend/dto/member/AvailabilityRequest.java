package com.devmozip.backend.dto.member;

import com.devmozip.backend.entity.Member.CollaborationStatus;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/users/me/availability 의 요청 본문.
 * 보낸 값만 바꾼다. contactHours를 빈 문자열로 보내면 연락 가능 시간을 비운다.
 */
public record AvailabilityRequest(
        CollaborationStatus collaborationStatus,

        @Size(max = 100, message = "연락 가능 시간은 100자 이내로 입력해 주세요.")
        String contactHours
) {}
