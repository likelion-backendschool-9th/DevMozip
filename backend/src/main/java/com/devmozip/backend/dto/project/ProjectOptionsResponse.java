package com.devmozip.backend.dto.project;

import java.util.Arrays;
import java.util.List;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.entity.enums.Skill;
import com.devmozip.backend.entity.enums.Interest;

public record ProjectOptionsResponse(List<JobRoleOption> jobRoles, List<String> techStacks, List<String> interests) {
	public record JobRoleOption(String code, String label) {}
	public static ProjectOptionsResponse defaults() {
		return new ProjectOptionsResponse(Arrays.stream(ProjectJobRole.values())
			.map(role -> new JobRoleOption(role.name(), role.getLabel())).toList(), Skill.labels(), Interest.labels());
	}
}
