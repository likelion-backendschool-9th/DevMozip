package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.enums.JobCategory;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RecruitmentRoleCondition(String name,
	@JsonDeserialize(using = StrictIntegerDeserializer.class) Integer count) {
	public RecruitmentRoleCondition {
		if (name == null || !JobCategory.contains(name.strip())) {
			throw new IllegalArgumentException("모집 직군을 확인해 주세요.");
		}
		name = name.strip();
		if (count == null || count < 1) throw new IllegalArgumentException("모집 인원은 1명 이상의 정수여야 합니다.");
	}

	@JsonAnySetter
	public void unknown(String key, Object value) {
		throw new IllegalArgumentException("알 수 없는 모집 조건 항목입니다: " + key);
	}
}
