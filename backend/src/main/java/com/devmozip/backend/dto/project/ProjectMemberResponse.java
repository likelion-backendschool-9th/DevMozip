package com.devmozip.backend.dto.project;
import java.time.LocalDateTime;
public record ProjectMemberResponse(long id,long userId,String nickname,String tag,String role,String jobRole,String status,LocalDateTime joinedAt) {}
