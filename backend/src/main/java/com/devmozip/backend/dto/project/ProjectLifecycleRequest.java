package com.devmozip.backend.dto.project;

import java.time.LocalDate;
import com.devmozip.backend.entity.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;

public record ProjectLifecycleRequest(
	@NotNull Action action, ProjectStatus status, LocalDate recruitmentEnd, LocalDate developmentEnd
) {
	public enum Action { CHANGE_STATUS, EXTEND_PERIOD, REQUEST_END, CANCEL_END_REQUEST }
}
