package com.devmozip.backend.dto.project;

/** 공개 가능한 공고 정보만 제공한다. 지원서·거절 사유·전체 멤버 개인정보를 포함하지 않는다. */
public record ProjectDetailResponse(ProjectResponse project, long memberCount, boolean recruitmentClosed) {}
