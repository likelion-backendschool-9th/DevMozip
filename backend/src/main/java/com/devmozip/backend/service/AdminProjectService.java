package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminActionRequest;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminProjectResponse;
import com.devmozip.backend.dto.admin.AdminRestrictRequest;

/** WBS 14.4 프로젝트 공고 관리, 14.5 표시 여부 (docs/admin-plan.md 5.5, D2·D3). */
public interface AdminProjectService {
	/** q는 제목 부분 일치, status는 ProjectStatus 이름, restricted는 true·false다. */
	AdminPageResponse<AdminProjectResponse> list(String q, String status, String restricted, int page, int size);

	/** 관리자 삭제. 주최자 삭제(7.5)와 같이 모집 중인 공고만 논리 삭제한다. */
	void act(long id, AdminActionRequest request);

	/** 관리자 숨김·복원. 숨긴 공고는 비공개 공고처럼 현재 팀원만 본다. */
	AdminProjectResponse restrict(long id, AdminRestrictRequest request);
}
