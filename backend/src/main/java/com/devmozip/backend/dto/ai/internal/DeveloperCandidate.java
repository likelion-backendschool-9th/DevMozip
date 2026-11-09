package com.devmozip.backend.dto.ai.internal;

import java.math.BigDecimal;
import java.util.List;

/** 공개 프로필에서 사용하는 필드만 조회한다. 인증·연락처·결제 정보는 포함하지 않는다. */
public record DeveloperCandidate(long id, String nickname, String tag, List<String> jobCategories,
	List<String> skills, List<String> interests, String career, BigDecimal trustScore) {}
