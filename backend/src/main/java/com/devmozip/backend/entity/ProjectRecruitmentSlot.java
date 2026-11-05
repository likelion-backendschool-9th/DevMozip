package com.devmozip.backend.entity;

import com.devmozip.backend.entity.enums.ProjectJobRole;

/** projects.recruitment_slots JSONB의 원소이며 엔티티/별도 테이블이 아니다. */
public record ProjectRecruitmentSlot(ProjectJobRole roleCode, int capacity, int minimumYears) {}
