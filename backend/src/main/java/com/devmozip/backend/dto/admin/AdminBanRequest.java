package com.devmozip.backend.dto.admin;

import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.utils.StrictStringDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 회원 제재 (WBS 14.3). status는 WARNED(경고)·SUSPENDED(정지)·ACTIVE(해제)다.
 * reason은 경고·정지에 필수이고 앞뒤 공백을 뺀 300자 이하다(Mock 입력 제한과 같다). reportId는 근거 신고이며 선택이다.
 * 해제(ACTIVE)는 유효한 제재의 revoked_at만 채우므로 reason·reportId를 받지 않는다(ERD에 칼럼이 없다).
 */
public record AdminBanRequest(
	@NotNull(message = "변경할 상태를 확인해 주세요.") MemberStatus status,
	@JsonDeserialize(using = StrictStringDeserializer.class) String reason,
	Long reportId) {
}
