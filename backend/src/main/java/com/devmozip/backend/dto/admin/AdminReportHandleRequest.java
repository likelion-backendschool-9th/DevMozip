package com.devmozip.backend.dto.admin;

import com.devmozip.backend.entity.Report;
import com.devmozip.backend.utils.StrictStringDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 신고 처리 (WBS 14.8). 규칙은 기존 프로젝트 신고 관리와 같다(docs/project-reports.md):
 * status는 REVIEWING·RESOLVED·REJECTED, 완료·기각은 resolution이 필수이고 1,000자 이하다. 처리자는 요청 값으로 받지 않는다.
 */
public record AdminReportHandleRequest(
	@NotNull(message = "처리 상태를 확인해 주세요.") Report.Status status,
	@JsonDeserialize(using = StrictStringDeserializer.class) String resolution) {
}
