package com.devmozip.backend.dto.admin;

import com.devmozip.backend.utils.StrictStringDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * PUT /api/v1/admin/projects/{id}, PUT /api/v1/admin/posts/{id}의 관리자 조치 (docs/admin-plan.md D3).
 * 지금 받는 조치는 DELETE(논리 삭제) 하나다. 되돌리는 조치는 없다.
 */
public record AdminActionRequest(
	@NotNull(message = "조치를 확인해 주세요.") @JsonDeserialize(using = StrictStringDeserializer.class) String action) {

	public static final String DELETE = "DELETE";

	public boolean isDelete() {
		return DELETE.equals(action);
	}
}
