package com.devmozip.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 비공개 계정 판정 기준(docs/privacy-settings-plan.md 2.3·4.2). 본인·다른 회원·비로그인 × 공개·비공개. 목록 조건의 실제 SQL 동작은 AccountLockIntegrationTest가 본다. */
class AccountVisibilityPolicyTest {
	final AccountVisibilityPolicy policy = new AccountVisibilityPolicy();

	// 판정은 요청자 ID만 받으므로 요청자의 역할(관리자·프리미엄)은 결과에 영향을 줄 수 없다.
	static Member member(long id, boolean isPrivate) {
		Member member = Member.builder().email(id + "@test.local").nickname("회원" + id).role(MemberRole.USER).status(MemberStatus.ACTIVE).build();
		ReflectionTestUtils.setField(member, "id", id);
		member.changePrivacy(isPrivate);
		return member;
	}

	@Test
	void publicAccountIsVisibleToEveryone() {
		Member target = member(1, false);
		assertTrue(policy.canView(1L, target));
		assertTrue(policy.canView(2L, target));
		assertTrue(policy.canView(null, target));
		assertDoesNotThrow(() -> policy.requireVisibleAccount(null, target));
		assertDoesNotThrow(() -> policy.requireVisibleContent(2L, target, "게시글을 찾을 수 없습니다."));
	}

	@Test
	void privateAccountIsVisibleOnlyToOwner() {
		Member target = member(1, true);
		assertTrue(policy.canView(1L, target));
		assertFalse(policy.canView(2L, target));
		assertFalse(policy.canView(null, target));
		assertDoesNotThrow(() -> policy.requireVisibleAccount(1L, target));
		assertDoesNotThrow(() -> policy.requireVisibleContent(1L, target, "게시글을 찾을 수 없습니다."));
	}

	@Test
	void personRejectionIsForbiddenAndContentRejectionIsNotFound() {
		Member target = member(1, true);
		ResponseStatusException person = assertThrows(ResponseStatusException.class, () -> policy.requireVisibleAccount(2L, target));
		assertEquals(HttpStatus.FORBIDDEN, person.getStatusCode());
		assertEquals("비공개 계정입니다.", person.getReason());

		ResponseStatusException content = assertThrows(ResponseStatusException.class,
			() -> policy.requireVisibleContent(null, target, "게시글을 찾을 수 없습니다."));
		assertEquals(HttpStatus.NOT_FOUND, content.getStatusCode());
		assertEquals("게시글을 찾을 수 없습니다.", content.getReason());
	}

	@Test
	void listConditionUsesViewerOnlyWhenSignedIn() {
		assertEquals("u.is_private = FALSE", AccountVisibilityPolicy.visibleAccountCondition("u", null));
		assertEquals("(u.is_private = FALSE OR u.id = :viewerId)", AccountVisibilityPolicy.visibleAccountCondition("u", 7L));
		assertEquals("(author.is_private = FALSE OR author.id = :viewerId)", AccountVisibilityPolicy.visibleAccountCondition("author", 7L));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "u.id", "u; DROP TABLE users", "U", "1u", "u OR TRUE"})
	void listConditionRejectsUnexpectedAlias(String alias) {
		assertThrows(IllegalArgumentException.class, () -> AccountVisibilityPolicy.visibleAccountCondition(alias, null));
	}
}
