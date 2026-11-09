package com.devmozip.backend.dto.project;

public record ProjectLifecycleResponse(
	ProjectResponse project, long activeMemberCount, long endRequestCount,
	long requiredEndRequestCount, boolean myEndRequested
) {}
