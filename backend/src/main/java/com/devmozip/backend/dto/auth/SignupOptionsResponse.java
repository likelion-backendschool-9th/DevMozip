package com.devmozip.backend.dto.auth;

import java.util.List;

/** GET /api/v1/auth/signup/options 의 응답 본문. 회원가입 화면의 선택지다. */
public record SignupOptionsResponse(List<String> jobCategories, List<String> skills, List<String> interests) {}
