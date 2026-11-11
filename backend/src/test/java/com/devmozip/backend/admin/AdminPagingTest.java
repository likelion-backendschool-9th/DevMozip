package com.devmozip.backend.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.devmozip.backend.service.AdminPaging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 목록의 페이지·검색어 규칙 (docs/admin-plan.md 2.3, 4.4). */
class AdminPagingTest {
	@Test
	void acceptsPageAndSizeInRange() {
		assertEquals(0, AdminPaging.request(0, 1).getPageNumber());
		assertEquals(50, AdminPaging.request(3, 50).getPageSize());
	}

	@ParameterizedTest
	@CsvSource({"-1,20", "0,0", "0,51"})
	void rejectsOutOfRange(int page, int size) {
		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> AdminPaging.request(page, size));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
	}

	@Test
	void keywordIsTrimmedLowercasedAndEscaped() {
		assertNull(AdminPaging.keyword(null));
		assertNull(AdminPaging.keyword("   "));
		assertEquals("%kim%", AdminPaging.keyword("  KIM "));
		assertEquals("%100\\%\\_a\\\\b%", AdminPaging.keyword("100%_a\\b"));
	}
}
