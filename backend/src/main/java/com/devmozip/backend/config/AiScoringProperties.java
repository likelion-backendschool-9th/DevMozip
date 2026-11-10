package com.devmozip.backend.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ai.recommendation")
public record AiScoringProperties(
	@DefaultValue("30") BigDecimal roleWeight,
	@DefaultValue("30") BigDecimal skillWeight,
	@DefaultValue("20") BigDecimal experienceWeight,
	@DefaultValue("10") BigDecimal trustWeight,
	@DefaultValue("50") BigDecimal trustCeiling,
	@DefaultValue("35") BigDecimal projectInterestWeight,
	@DefaultValue("35") BigDecimal projectSkillWeight,
	@DefaultValue("20") BigDecimal projectTextWeight,
	@DefaultValue("10") BigDecimal projectStatusWeight) {
	public static final BigDecimal BASE_TEMPERATURE = new BigDecimal("36.5");
	public static final int RESULT_LIMIT = 10;
	public static final int QUERY_BATCH_SIZE = 200;
	public static final BigDecimal RECRUITMENT_COMPLETED_RATIO = new BigDecimal("0.5");
	public static final BigDecimal IN_PROGRESS_RATIO = new BigDecimal("0.3");

	public AiScoringProperties {
		for (BigDecimal weight : new BigDecimal[]{roleWeight, skillWeight, experienceWeight, trustWeight,
			projectInterestWeight, projectSkillWeight, projectTextWeight, projectStatusWeight}) {
			if (weight == null || weight.signum() <= 0) throw new IllegalArgumentException("추천 배점은 양수여야 합니다.");
		}
		if (trustCeiling == null || trustCeiling.compareTo(BASE_TEMPERATURE) <= 0) {
			throw new IllegalArgumentException("신뢰 온도 상한은 36.5도보다 커야 합니다.");
		}
	}
}
