package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.devmozip.backend.utils.StrictIntegerDeserializer;
import com.devmozip.backend.utils.StrictStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RecruitmentRoleConditionDto(@JsonDeserialize(using = StrictStringDeserializer.class) String name,
	@JsonDeserialize(using = StrictIntegerDeserializer.class) Integer count) {
	public RecruitmentRoleConditionDto {
		if (name == null || name.isBlank() || name.strip().length() > 30) {
			throw new IllegalArgumentException("모집 직군은 1~30자로 입력해 주세요.");
		}
		name = name.strip();
		if (count == null || count < 1 || count > 20) throw new IllegalArgumentException("모집 인원은 1~20명의 정수여야 합니다.");
	}

	@JsonAnySetter
	public void unknown(String key, Object value) {
		throw new IllegalArgumentException("알 수 없는 모집 조건 항목입니다: " + key);
	}
}
