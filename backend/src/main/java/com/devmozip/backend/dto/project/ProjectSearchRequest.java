package com.devmozip.backend.dto.project;

import java.util.List;
import com.devmozip.backend.entity.enums.ProjectStatus;
import jakarta.validation.constraints.*;

public record ProjectSearchRequest(
	@Size(max = 255) String query,
	@Size(max = 30) String jobRole,
	ProjectStatus status,
	@Size(max = 5) List<@NotBlank @Size(max = 30) String> interests,
	@Size(max = 5) List<@NotBlank @Size(max = 30) String> techStacks,
	Sort sort,
	@Min(0) Integer page,
	@Min(1) @Max(100) Integer size
) {
	public enum Sort { LATEST, POPULAR, DEADLINE }
	public Sort effectiveSort() { return sort == null ? Sort.LATEST : sort; }
	public int effectivePage() { return page == null ? 0 : page; }
	public int effectiveSize() { return size == null ? 20 : size; }
}
