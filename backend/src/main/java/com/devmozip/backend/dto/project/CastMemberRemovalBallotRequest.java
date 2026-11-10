package com.devmozip.backend.dto.project;
import jakarta.validation.constraints.NotNull;
public record CastMemberRemovalBallotRequest(@NotNull Boolean agree) {}
