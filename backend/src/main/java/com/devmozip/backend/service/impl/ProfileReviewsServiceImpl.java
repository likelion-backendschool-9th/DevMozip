package com.devmozip.backend.service.impl;

import com.devmozip.backend.dto.profile.ProfileReviewsResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.service.ProfileReviewsService;
import com.devmozip.backend.service.ProjectReviewService;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProfileReviewsServiceImpl implements ProfileReviewsService {
	private final MemberRepository members;
	private final ProjectReviewService reviews;
	private final com.devmozip.backend.repository.ProjectRepository projects;
	@Override public ProfileReviewsResponse own(String email,int page,int size){
		if(email==null||email.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
		if(page<0||page>1000000||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페이지와 조회 개수를 확인해 주세요.");
		try{
			var member=members.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"회원 정보를 찾을 수 없습니다."));
			if(member.getStatus()!=Member.MemberStatus.ACTIVE&&member.getStatus()!=Member.MemberStatus.WARNED)
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,"이 계정으로 리뷰를 조회할 수 없습니다.");
			return list(member.getId(),true,page,size);
		}catch(DataAccessException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"받은 리뷰를 조회할 수 없습니다.");}
	}
	@Override public ProfileReviewsResponse publicList(String slug,int page,int size){
		if(page<0||page>1000000||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페이지와 조회 개수를 확인해 주세요.");
		if(slug==null||!slug.matches("[A-Za-z0-9_-]{1,100}")||slug.equalsIgnoreCase("me"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"프로필 정보를 찾을 수 없습니다.");
		try{
			var member=members.findByTagAndStatus(slug,Member.MemberStatus.ACTIVE).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"프로필 정보를 찾을 수 없습니다."));
			return list(member.getId(),false,page,size);
		}catch(DataAccessException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"받은 리뷰를 조회할 수 없습니다.");}
	}
	private ProfileReviewsResponse list(long userId,boolean own,int page,int size){
			// 기존 프로젝트 서비스의 SUBMITTED·별점·키워드 연결 집계를 재사용한다.
			var result=reviews.received(userId);
			var visibleProjects=own?java.util.Set.<Long>of():projects.findAllById(result.reviews().stream().map(r->r.projectId()).distinct().toList()).stream()
				.filter(p->p.isPublic()&&p.getDeletedAt()==null).map(p->p.getId()).collect(java.util.stream.Collectors.toSet());
			var items=result.reviews().stream().sorted(java.util.Comparator
				.comparing(com.devmozip.backend.dto.project.ProjectReviewSummaryResponse.Received::submittedAt,java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
				.thenComparing(com.devmozip.backend.dto.project.ProjectReviewSummaryResponse.Received::id,java.util.Comparator.reverseOrder()))
				.skip((long)page*size).limit(size).map(r->new ProfileReviewsResponse.Item(r.id(),own||visibleProjects.contains(r.projectId())?r.projectId():null,own||visibleProjects.contains(r.projectId())?r.projectTitle():"비공개 프로젝트",r.authorUserId(),r.authorNickname(),r.authorTag(),
					r.rating(),r.keywords(),r.submittedAt()==null?null:r.submittedAt().atOffset(ZoneOffset.ofHours(9)))).toList();
			return new ProfileReviewsResponse(userId,result.count(),result.average(),result.distribution(),
				result.keywords().stream().map(k->new ProfileReviewsResponse.Keyword(k.name(),k.count())).toList(),items,page,size,
				result.count()/size+(result.count()%size==0?0:1));
	}

}
