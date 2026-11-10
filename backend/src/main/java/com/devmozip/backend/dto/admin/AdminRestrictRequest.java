package com.devmozip.backend.dto.admin;

import com.devmozip.backend.utils.StrictBooleanDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/** 관리자 숨김·복원 (WBS 14.5·14.7). 원하는 최종 값을 받으므로 같은 값을 다시 보내도 결과가 같다. JSON boolean만 받는다. */
public record AdminRestrictRequest(
	@NotNull(message = "숨김 여부를 확인해 주세요.") @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean restricted) {
}
