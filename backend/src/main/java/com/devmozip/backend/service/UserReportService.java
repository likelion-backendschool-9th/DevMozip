package com.devmozip.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.entity.enums.UserReportReason;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 사용자 신고 접수. 저장까지만 하고 관리자 처리는 포함하지 않는다. */
@Service
public class UserReportService {
	private final MemberRepository members;
	private final ReportRepository reports;
	private final Clock clock;

	public UserReportService(MemberRepository members, ReportRepository reports, @Qualifier("messengerClock") Clock clock) {
		this.members = members;
		this.reports = reports;
		this.clock = clock;
	}

	@Transactional
	public void report(String reporterEmail, String targetSlug, String reason) {
		Member reporter = members.findByEmail(reporterEmail)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 회원을 찾을 수 없습니다."));
		Member target = members.findByTag(targetSlug)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고 대상을 찾을 수 없습니다."));
		if (target.getId().equals(reporter.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인은 신고할 수 없습니다.");
		}
		if (!UserReportReason.contains(reason)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유를 확인해 주세요.");
		}

		// 중복 판정의 DB 제약은 팀원 신고(#51)와 함께 정한다. 그 전까지 신고자 행을 잠가 동시 요청의 중복을 막는다.
		members.findForMessengerById(reporter.getId());
		if (reports.existsByReporter_IdAndReportedUser_Id(reporter.getId(), target.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 사용자입니다.");
		}
		reports.save(Report.ofUser(reporter, target, reason, LocalDateTime.now(clock)));
	}
}
