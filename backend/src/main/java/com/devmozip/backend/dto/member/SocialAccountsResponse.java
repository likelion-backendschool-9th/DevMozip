package com.devmozip.backend.dto.member;

/** GET /api/v1/users/me/social-accounts 의 응답 본문. 카카오 회원번호와 비밀번호 해시는 내보내지 않는다. */
public record SocialAccountsResponse(boolean kakaoLinked, boolean passwordSet) {}
