package com.devmozip.backend.service;

import com.devmozip.backend.dto.portfolio.PortfolioDetailResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ProjectPortfolioSnapshotService {
	private final ProjectApplicationRepository applications;
	private final ProjectApplicationPortfolioSnapshotRepository snapshots;
	private final MemberRepository users;
	private final ProjectCurrentUserProvider identity;
	private final ObjectMapper mapper;
	public PortfolioDetailResponse detail(long projectId,long applicationId,long snapshotId){
		long viewer=identity.getCurrentUserId();
		var application=applications.findByIdAndProjectId(applicationId,projectId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"지원 내역을 찾을 수 없습니다."));
		if(viewer!=application.getApplicantMemberId()){
			if(viewer!=application.getProject().getHostMemberId())throw error(HttpStatus.NOT_FOUND,"지원 내역을 찾을 수 없습니다.");
			var member=users.findById(viewer).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."));
			if(member.getRole()!=Member.MemberRole.PREMIUM&&member.getRole()!=Member.MemberRole.ADMIN)throw error(HttpStatus.FORBIDDEN,"지원 포트폴리오 상세 열람은 프리미엄 기능입니다.");
		}
		var copy=snapshots.findById(snapshotId).filter(s->s.getSubmission().getApplication().getId().equals(application.getId())).orElseThrow(()->error(HttpStatus.NOT_FOUND,"제출 포트폴리오를 찾을 수 없습니다."));
		var data=mapper.readValue(copy.getContent(),PortfolioDetailResponse.class);
		return new PortfolioDetailResponse(data.portfolio(),data.author(),data.description(),data.attachments(),data.document(),false);
	}
	private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
