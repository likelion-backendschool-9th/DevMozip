package com.devmozip.backend.service.impl;

import com.devmozip.backend.dto.profile.ProfilePostResponse;
import com.devmozip.backend.dto.profile.ProfilePostsPageResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.ProfilePostsQueryRepository;
import com.devmozip.backend.service.ProfilePostsService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class ProfilePostsServiceImpl implements ProfilePostsService {
	private final MemberRepository members;
	private final ProfilePostsQueryRepository query;

	@Override public ProfilePostsPageResponse own(String email, int page, int size) {
		validate(page,size);
		if(email==null||email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
		return available(()->{
			var member=members.findByEmail(email).orElseThrow(ProfilePostsServiceImpl::missing);
			if(member.getStatus()!=Member.MemberStatus.ACTIVE&&member.getStatus()!=Member.MemberStatus.WARNED)
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,"이 계정으로 목록을 조회할 수 없습니다.");
			return list(member.getId(),page,size);
		});
	}
	@Override public ProfilePostsPageResponse publicList(String slug, int page, int size) {
		validate(page,size);
		if(slug==null||!slug.matches("[A-Za-z0-9_-]{1,100}")||slug.equalsIgnoreCase("me")) throw missing();
		return available(()->list(members.findByTagAndStatus(slug,Member.MemberStatus.ACTIVE)
			.orElseThrow(ProfilePostsServiceImpl::missing).getId(),page,size));
	}
	private ProfilePostsPageResponse list(long userId,int page,int size) {
		var result=query.findByUserId(userId,page,size);
		return new ProfilePostsPageResponse(result.rows().stream().map(this::response).toList(),page,size,result.total(),
			result.total()/size+(result.total()%size==0?0:1));
	}
	private ProfilePostResponse response(ProfilePostsQueryRepository.Row r) {
		var original=r.original()==null?null:new ProfilePostResponse.OriginalPost(r.original().id(),r.original().contentPreview(),author(r.original().author()));
		return new ProfilePostResponse(r.id(),r.type(),r.category(),r.title(),r.contentPreview(),author(r.author()),atSeoul(r.createdAt()),original);
	}
	private ProfilePostResponse.Author author(ProfilePostsQueryRepository.Author a) {
		return new ProfilePostResponse.Author(a.id(),a.nickname(),a.tag(),a.profileImage());
	}
	private OffsetDateTime atSeoul(LocalDateTime date){return date==null?null:date.atOffset(ZoneOffset.ofHours(9));}
	private void validate(int page,int size){
		if(page<0||page>1000000||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페이지와 조회 개수를 확인해 주세요.");
	}
	private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"프로필 정보를 찾을 수 없습니다.");}
	private <T>T available(Supplier<T> action){
		try{return action.get();}catch(DataAccessException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포스팅 목록을 조회할 수 없습니다.");}
	}
}
