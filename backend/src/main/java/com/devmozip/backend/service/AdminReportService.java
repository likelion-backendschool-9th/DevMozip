package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminReportHandleRequest;
import com.devmozip.backend.dto.admin.AdminReportResponse;

/**
 * WBS 14.8 신고 내역 처리 (docs/admin-plan.md 5.7, D5). 공고·팀원·사용자·게시글·댓글 신고를 한 API로 다룬다.
 * 처리 결과만 기록하며 제재·숨김·삭제는 자동으로 하지 않는다.
 */
public interface AdminReportService {
	/** type은 PROJECT·MEMBER·USER·POST·COMMENT, status는 AWATING·REVIEWING·RESOLVED·REJECTED다. 최신 신고 ID순이다. */
	AdminPageResponse<AdminReportResponse> list(String type, String status, int page, int size);

	AdminReportResponse handle(long id, AdminReportHandleRequest request);
}
