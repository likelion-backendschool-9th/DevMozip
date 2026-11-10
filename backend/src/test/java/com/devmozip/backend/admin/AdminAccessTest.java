package com.devmozip.backend.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 확인 (docs/admin-plan.md 4.2). JWT 역할과 별도로 DB의 역할·상태를 본다. */
class AdminAccessTest {
	private final CurrentMemberProvider current = mock(CurrentMemberProvider.class);
	private final AdminAccess access = new AdminAccess(current);

	private static Member member(Member.MemberRole role, Member.MemberStatus status) {
		return Member.builder().email("admin@test.local").nickname("관리자").tag("admin").role(role).status(status).build();
	}

	@Test
	void activeAdminPasses() {
		Member admin = member(Member.MemberRole.ADMIN, Member.MemberStatus.ACTIVE);
		when(current.find()).thenReturn(Optional.of(admin));
		assertSame(admin, access.require());
	}

	@Test
	void anonymousIsUnauthorized() {
		when(current.find()).thenReturn(Optional.empty());
		ResponseStatusException e = assertThrows(ResponseStatusException.class, access::require);
		assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
	}

	@ParameterizedTest
	@CsvSource({"USER,ACTIVE", "PREMIUM,ACTIVE", "OWNER,ACTIVE", "ADMIN,WARNED", "ADMIN,SUSPENDED"})
	void otherRolesOrStatusesAreForbidden(Member.MemberRole role, Member.MemberStatus status) {
		when(current.find()).thenReturn(Optional.of(member(role, status)));
		ResponseStatusException e = assertThrows(ResponseStatusException.class, access::require);
		assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
		assertEquals("관리자만 이용할 수 있습니다.", e.getReason());
	}

	@Test
	void withdrawnRowIsForbidden() {
		// 탈퇴 회원 행은 role·status가 NULL이다(#135).
		when(current.find()).thenReturn(Optional.of(member(null, null)));
		assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, access::require).getStatusCode());
	}
}
