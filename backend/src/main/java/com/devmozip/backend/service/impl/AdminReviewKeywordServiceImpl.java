package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.admin.AdminReviewKeywordsRequest;
import com.devmozip.backend.dto.admin.AdminReviewKeywordsResponse;
import com.devmozip.backend.entity.ReviewKeyword;
import com.devmozip.backend.repository.AdminReviewKeywordRepository;
import com.devmozip.backend.repository.ReviewKeywordRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminReviewKeywordService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 리뷰 키워드 관리 (docs/admin-plan.md 5.8). 리뷰 제출은 활성 키워드만 받으므로(ProjectReviewService) 바뀐 선택지가 바로 반영된다.
 * 기존 키워드 행을 잠가 목록 수정끼리 하나씩 처리하고, 같은 이름의 동시 추가는 409로 알린다.
 */
@Service
public class AdminReviewKeywordServiceImpl implements AdminReviewKeywordService {
	static final int MAX_NAME_LENGTH = 30;

	private final AdminAccess access;
	private final AdminReviewKeywordRepository query;
	private final ReviewKeywordRepository keywords;
	private final Clock clock;

	public AdminReviewKeywordServiceImpl(AdminAccess access, AdminReviewKeywordRepository query, ReviewKeywordRepository keywords,
		@Qualifier("adminClock") Clock clock) {
		this.access = access;
		this.query = query;
		this.keywords = keywords;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminReviewKeywordsResponse list() {
		access.require();
		return AdminReviewKeywordsResponse.of(query.findAllOrdered());
	}

	@Override
	@Transactional
	public AdminReviewKeywordsResponse replace(AdminReviewKeywordsRequest request) {
		access.require();
		Set<String> desired = names(request.keywords());
		LocalDateTime now = LocalDateTime.now(clock);
		Map<String, ReviewKeyword> existing = query.lockAll().stream().collect(Collectors.toMap(ReviewKeyword::getName, Function.identity()));
		existing.values().forEach(keyword -> keyword.changeActive(desired.contains(keyword.getName()), now));
		// IDENTITY 키라 save가 바로 INSERT한다. 이름 UNIQUE 충돌은 save와 flush 어디서든 날 수 있다.
		try {
			desired.stream().filter(name -> !existing.containsKey(name)).forEach(name -> keywords.save(new ReviewKeyword(name, now)));
			keywords.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 관리자가 같은 키워드를 먼저 추가했습니다. 목록을 다시 불러와 주세요.");
		}
		return AdminReviewKeywordsResponse.of(query.findAllOrdered());
	}

	private static Set<String> names(List<String> values) {
		Set<String> names = new LinkedHashSet<>();
		for (String value : values) {
			String name = value == null ? "" : value.strip();
			if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "키워드는 1~30자로 입력해 주세요.");
			}
			if (!names.add(name)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 키워드를 두 번 넣을 수 없습니다.");
			}
		}
		return names;
	}
}
