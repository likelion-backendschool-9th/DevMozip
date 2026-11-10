package com.devmozip.backend.dto.admin;

import java.util.List;
import com.devmozip.backend.utils.StrictStringDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 리뷰 키워드 목록 수정 (WBS 14.9). keywords는 활성으로 둘 이름의 목록이다. 빈 목록도 받는다(키워드 선택은 리뷰의 필수 항목이 아니다).
 * 이름은 앞뒤 공백을 뺀 1~30자(Mock 입력 제한과 같다)이며 목록 안에서 중복될 수 없다.
 */
public record AdminReviewKeywordsRequest(
	@NotNull(message = "키워드 목록을 확인해 주세요.") @JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> keywords) {
}
