package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.enums.RequiredCareer;
import com.devmozip.backend.utils.StrictIntegerDeserializer;
import com.devmozip.backend.utils.StrictStringDeserializer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateConditionsRequest(
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> interests,
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> stacks,
	List<RecruitmentRoleConditionDto> roles, RequiredCareer career,
	@JsonDeserialize(using = StrictIntegerDeserializer.class) Integer durationMonths) {
	public UpdateConditionsRequest {
		if (career == null) throw new IllegalArgumentException("요구 경력을 선택해 주세요.");
		if (durationMonths == null) throw new IllegalArgumentException("프로젝트 기간을 입력해 주세요.");
		var draft = new DraftConditionsRequest(interests, stacks, roles, career, durationMonths);
		if (draft.interests().isEmpty()) throw new IllegalArgumentException("관심 분야를 하나 이상 선택해 주세요.");
		interests = draft.interests();
		stacks = draft.stacks();
		roles = draft.roles();
	}

	@JsonAnySetter
	public void unknown(String key, Object value) {
		throw new IllegalArgumentException("알 수 없는 조건 항목입니다: " + key);
	}
}
