package com.devmozip.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.devmozip.backend.dto.feed.FeedPostRequest;
import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.entity.enums.PostCategory;
import com.devmozip.backend.entity.enums.PostReportReason;
import com.devmozip.backend.entity.enums.PostVisibility;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 피드 작성·조회가 함께 쓰는 입력 규칙과 열람 조건 (docs/feed-plan.md).
 * 입력 제한과 안내 문구는 현재 작성 화면(lib/social/socialService.ts)과 같다.
 */
public final class FeedRules {
	public static final int MAX_CONTENT_LENGTH = 10_000;
	public static final int MAX_OPINION_LENGTH = 1_000;
	public static final int MAX_TAGS = 30;
	public static final int MAX_TAG_LENGTH = 30;
	public static final int MAX_QUERY_LENGTH = 100;
	public static final int MAX_PAGE = 1_000_000;
	public static final int MAX_SIZE = 100;

	private FeedRules() {
	}

	/** 검증을 마친 원본 글 입력 */
	public record PostInput(PostCategory category, String title, String content, List<String> techStacks, List<String> interests) {}

	public static PostInput post(FeedPostRequest request) {
		if (request == null) throw badRequest("게시글 내용을 확인해 주세요.");
		PostCategory category = category(request.category());
		String title = request.title() == null ? "" : request.title().trim();
		if (title.isEmpty() || title.length() > Post.MAX_TITLE_LENGTH) throw badRequest("제목을 1~150자로 입력해 주세요.");
		String content = request.content();
		// 본문은 화면처럼 앞뒤 공백·줄바꿈을 그대로 저장한다.
		if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
			throw badRequest("본문을 1~10,000자로 입력해 주세요.");
		}
		List<String> stacks = tags(request.techStacks(), "기술 스택 태그는 중복 없이 1~30자로 최대 30개까지 입력해 주세요.");
		List<String> interests = tags(request.interests(), "관심 분야는 중복 없이 1~30자로 최대 30개까지 입력해 주세요.");
		return new PostInput(category, title, content, stacks, interests);
	}

	/** 퍼가기 의견. 비어 있으면 의견 없이 퍼간다. */
	public static String opinion(String content) {
		if (content != null && content.length() > MAX_OPINION_LENGTH) throw badRequest("퍼가기 의견은 1,000자 이내로 입력해 주세요.");
		return content == null || content.isBlank() ? null : content;
	}

	public static String comment(String content) {
		String trimmed = content == null ? "" : content.trim();
		if (trimmed.isEmpty() || content.length() > Comment.MAX_CONTENT_LENGTH) throw badRequest("댓글은 1~1,000자로 입력해 주세요.");
		return trimmed;
	}

	public static String reportReason(String reason) {
		if (!PostReportReason.contains(reason)) throw badRequest("신고 사유를 선택해 주세요.");
		return reason;
	}

	/** 목록 조건의 카테고리. 없으면 전체다. */
	public static PostCategory categoryFilter(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return PostCategory.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw badRequest("조회 조건을 확인해 주세요.");
		}
	}

	/** 검색어를 공백으로 나눈다. 모든 단어가 들어 있는 글을 찾는다(목업과 같다). */
	public static List<String> searchTerms(String query) {
		if (query == null) return List.of();
		if (query.length() > MAX_QUERY_LENGTH) throw badRequest("검색어는 100자 이내로 입력해 주세요.");
		String trimmed = query.trim();
		return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
	}

	public static void page(int page, int size) {
		if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) throw badRequest("페이지와 조회 개수를 확인해 주세요.");
	}

	/** 이용 가능한 회원(정상·경고). 정지 회원의 글과 댓글은 보이지 않는다. */
	public static boolean usable(Member member) {
		return member != null && (member.getStatus() == Member.MemberStatus.ACTIVE || member.getStatus() == Member.MemberStatus.WARNED);
	}

	/** 삭제되지 않았고, 공개이거나 요청자가 작성자인 글. 숨긴 글(HIDDEN·RESTRICTED)은 작성자만 볼 수 있다. */
	public static boolean readable(Post post, Long viewerId) {
		if (post == null || post.isDeleted() || !usable(post.getAuthor())) return false;
		return post.getVisibility() == PostVisibility.VISIBLE || (viewerId != null && post.isWrittenBy(viewerId));
	}

	/** 퍼간 글 화면에 원본 내용을 보여 줄 수 있는지. 원본이 숨김이면 작성자 본인이 아니어도 원본 삭제 안내를 표시한다. */
	public static boolean originalAvailable(Post original) {
		return original != null && !original.isDeleted() && original.getVisibility() == PostVisibility.VISIBLE && usable(original.getAuthor());
	}

	public static ResponseStatusException postNotFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
	}

	public static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private static PostCategory category(String value) {
		if (value == null) throw badRequest("카테고리를 선택해 주세요.");
		try {
			return PostCategory.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw badRequest("카테고리를 선택해 주세요.");
		}
	}

	/** 앞뒤 공백을 지우고 1~30자, 대소문자를 무시한 중복 없음, 최대 30개 */
	private static List<String> tags(List<String> values, String message) {
		if (values == null) return List.of();
		if (values.size() > MAX_TAGS) throw badRequest(message);
		List<String> tags = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String value : values) {
			String tag = value == null ? "" : value.trim();
			if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH || !seen.add(tag.toLowerCase(Locale.ROOT))) throw badRequest(message);
			tags.add(tag);
		}
		return tags;
	}
}
