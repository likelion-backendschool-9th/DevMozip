package com.devmozip.backend.dto.member;

/**
 * GET /api/v1/users/me 응답. username·role은 기존 계약(토큰의 주인과 역할)이다.
 * status는 DB의 계정 상태이고, warningReason은 경고(WARNED) 회원의 최근 제재 사유다(docs/admin-plan.md D8). 그 밖에는 null이다.
 */
public record MeResponse(String username, String role, String status, String warningReason) {
}
