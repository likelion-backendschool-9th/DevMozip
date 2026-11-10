package com.devmozip.backend.dto.project;
import jakarta.validation.constraints.*;
public record CreateMemberRemovalVoteRequest(@Positive long projectMemberId,@NotBlank @Size(max=200) String reason) {}
