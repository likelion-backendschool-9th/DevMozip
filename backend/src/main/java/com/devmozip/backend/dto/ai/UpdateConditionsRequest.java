package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.enums.Interest;
import com.devmozip.backend.entity.enums.Skill;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateConditionsRequest(
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> fields,
	List<String> interests,
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> stacks,
	List<RecruitmentRoleCondition> roles, RequiredCareer career,
	@JsonDeserialize(using = StrictIntegerDeserializer.class) Integer durationMonths) {
	public UpdateConditionsRequest {
		if (career == null) throw new IllegalArgumentException("요구 경력을 선택해 주세요.");
		if (durationMonths == null) throw new IllegalArgumentException("프로젝트 기간을 입력해 주세요.");
		fields = inputs(fields, 10, 30, "프로젝트 분야");
		if (fields.isEmpty()) throw new IllegalArgumentException("프로젝트 분야를 하나 이상 입력해 주세요.");
		interests = choices(interests, Interest.labels(), "관심 분야");
		stacks = inputs(stacks, 30, 50, "기술 스택").stream()
			.map(value -> Skill.labels().stream().filter(known -> known.equalsIgnoreCase(value)).findFirst().orElse(value))
			.distinct().toList();
		if (roles == null) roles = List.of();
		var names = new HashSet<String>();
		for (RecruitmentRoleCondition role : roles) {
			if (role == null || !names.add(role.name())) {
				throw new IllegalArgumentException("모집 직군은 중복 없이 입력해 주세요.");
			}
		}
		roles = List.copyOf(roles);
		if (durationMonths < 1 || durationMonths > 36) {
			throw new IllegalArgumentException("프로젝트 기간은 1~36개월의 정수여야 합니다.");
		}
	}

	private static List<String> inputs(List<String> values, int maxCount, int maxLength, String label) {
		if (values == null) return List.of();
		if (values.size() > maxCount || values.stream().anyMatch(value -> value == null
			|| value.isBlank() || value.strip().length() > maxLength)) {
			throw new IllegalArgumentException(label + "는 " + maxCount + "개 이하, 항목당 1~" + maxLength + "자로 입력해 주세요.");
		}
		return values.stream().map(String::strip).distinct().toList();
	}

	private static List<String> choices(List<String> values, List<String> allowed, String label) {
		if (values == null) return List.of();
		if (values.stream().anyMatch(v -> v == null || !allowed.contains(v.strip()))) {
			throw new IllegalArgumentException(label + " 값을 확인해 주세요.");
		}
		return values.stream().map(String::strip).distinct().toList();
	}

	@JsonAnySetter
	public void unknown(String key, Object value) {
		throw new IllegalArgumentException("알 수 없는 조건 항목입니다: " + key);
	}
}
