package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminBanRequest;
import com.devmozip.backend.dto.admin.AdminMemberResponse;
import com.devmozip.backend.dto.admin.AdminPageResponse;

/**
 * WBS 14.2 회원 조회, 14.3 회원 제재 (docs/admin-plan.md 5.3·5.4). 요청자는 AdminAccess로 확인한다.
 * 탈퇴 회원은 목록과 제재 대상에서 뺀다.
 */
public interface AdminMemberService {
	/** q는 닉네임·이메일·tag 부분 일치, status·role은 enum 이름이다. 최신 회원 ID순이다. */
	AdminPageResponse<AdminMemberResponse> list(String q, String status, String role, int page, int size);

	/** 경고·정지·해제. 정지·해제는 커밋 뒤 정지 목록(SuspendedMemberStore)에 반영한다. */
	AdminMemberResponse ban(String slug, AdminBanRequest request);
}
