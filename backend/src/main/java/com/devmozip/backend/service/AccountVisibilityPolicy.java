package com.devmozip.backend.service;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.entity.Member;

/**
 * 비공개 계정(WBS 17.7 계정 잠금)의 공통 판정 기준. docs/privacy-settings-plan.md 2.3·4.2·5.3을 따른다.
 * 요청자가 대상 본인이거나 대상이 공개 계정이면 볼 수 있다. 관리자·프리미엄도 공개 조회에서는 예외가 아니다.
 * 요청자 ID는 각 도메인이 인증 정보로 찾은 값만 넘긴다. 비로그인이면 null이다.
 */
@Component
public class AccountVisibilityPolicy {
	public static final String PRIVATE_ACCOUNT_MESSAGE = "비공개 계정입니다.";
	/** 목록 조건이 쓰는 요청자 파라미터 이름 */
	public static final String VIEWER_PARAM = "viewerId";

	private static final Pattern SQL_ALIAS = Pattern.compile("[a-z_][a-z0-9_]*");

	/** 단건 조회용. 대상이 공개 계정이거나 요청자 본인이면 true다. */
	public boolean canView(Long viewerId, Member target) {
		return !target.isPrivate() || target.getId().equals(viewerId);
	}

	/** 사람 단위 조회(프로필과 하위 정보, 팔로우 상태·목록·팔로우 변경). 볼 수 없으면 403 "비공개 계정입니다."로 응답한다. */
	public void requireVisibleAccount(Long viewerId, Member target) {
		if (!canView(viewerId, target)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, PRIVATE_ACCOUNT_MESSAGE);
		}
	}

	/** 글 단위 조회·쓰기(게시글 상세·댓글, 포트폴리오 상세 등). 볼 수 없으면 없는 글과 같은 404로 응답한다. */
	public void requireVisibleContent(Long viewerId, Member author, String notFoundMessage) {
		if (!canView(viewerId, author)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
		}
	}

	/**
	 * 목록 조회의 SQL 조건. 응답에 넣은 뒤 거르지 않고 조회 조건에서 빼야 페이지 크기·hasNext·전체 수가 맞는다.
	 * 요청자가 있으면 {@code (u.is_private = FALSE OR u.id = :viewerId)}, 비로그인이면 {@code u.is_private = FALSE}다.
	 * 요청자가 있을 때 호출한 쪽은 {@link #VIEWER_PARAM} 파라미터에 요청자 ID를 넣는다.
	 * 상태가 없는 SQL 조각이라 Repository에서도 주입 없이 쓸 수 있게 static으로 둔다.
	 *
	 * @param usersAlias users 테이블의 별칭. 코드에서 정한 값만 넘기며 요청 값으로 만들지 않는다.
	 */
	public static String visibleAccountCondition(String usersAlias, Long viewerId) {
		if (!SQL_ALIAS.matcher(usersAlias).matches()) {
			throw new IllegalArgumentException("users 별칭을 확인해 주세요.");
		}
		String publicAccount = usersAlias + ".is_private = FALSE";
		return viewerId == null ? publicAccount : "(" + publicAccount + " OR " + usersAlias + ".id = :" + VIEWER_PARAM + ")";
	}
}
