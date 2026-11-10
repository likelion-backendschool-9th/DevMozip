package com.devmozip.backend.service.impl;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.search.DeveloperSearchItemResponse;
import com.devmozip.backend.dto.search.DeveloperSearchQuery;
import com.devmozip.backend.dto.search.SearchPageResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.DeveloperSearchRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.DeveloperSearchService;
import com.devmozip.backend.service.SearchKeywords;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 수와 목록이 한 시점의 값이 되도록 REPEATABLE_READ로 읽는다(팔로우 목록과 같다). */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DeveloperSearchServiceImpl implements DeveloperSearchService {
	private final DeveloperSearchRepository searches;
	private final MemberRepository members;
	private final CurrentMemberProvider currentMember;

	public DeveloperSearchServiceImpl(DeveloperSearchRepository searches, MemberRepository members, CurrentMemberProvider currentMember) {
		this.searches = searches;
		this.members = members;
		this.currentMember = currentMember;
	}

	@Override
	public SearchPageResponse<DeveloperSearchItemResponse> search(DeveloperSearchQuery query) {
		// 요청자는 인증 정보로만 찾는다. 쿼리로 받은 사용자 ID는 쓰지 않는다.
		Long requesterId = currentMember.find().map(Member::getId).orElse(null);
		if (query.following() && requesterId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "팔로잉 목록은 로그인 후 볼 수 있습니다.");
		}
		var criteria = new DeveloperSearchRepository.Criteria(SearchKeywords.tokens(query.keyword()), query.jobCategory(),
			query.career(), query.interests(), query.skills(), query.available(), query.following());
		var result = searches.search(criteria, requesterId, query.page(), query.size());

		var byId = members.findAllById(result.rows().stream().map(DeveloperSearchRepository.Row::id).toList()).stream()
			.collect(Collectors.toMap(Member::getId, Function.identity()));
		var items = result.rows().stream()
			.map(row -> {
				Member member = byId.get(row.id());
				return member == null ? null : DeveloperSearchItemResponse.from(member, row.followed());
			})
			.filter(Objects::nonNull)
			.toList();
		return SearchPageResponse.of(items, query.page(), query.size(), result.total());
	}
}
