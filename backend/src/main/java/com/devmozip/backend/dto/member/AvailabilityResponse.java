package com.devmozip.backend.dto.member;

import com.devmozip.backend.entity.Member.CollaborationStatus;

/** PATCH /api/v1/users/me/availability 의 응답 본문 */
public record AvailabilityResponse(CollaborationStatus collaborationStatus, String contactHours) {}
