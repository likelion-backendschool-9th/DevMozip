package com.devmozip.backend.message;

import java.time.LocalDateTime;
import java.util.Map;
import com.devmozip.backend.entity.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL과 local(H2) 프로필이 같은 신고 동작을 하는지 확인한다. */
abstract class UserReportApiTests extends MessengerApiTestSupport {
	String reports(Member target) {
		return "/api/v1/users/" + target.getTag() + "/reports";
	}

	int reportCount(Member reporter) {
		return jdbc.queryForObject("SELECT count(*) FROM reports WHERE reporter_id = ?", Integer.class, reporter.getId());
	}

	@Test void requiresLogin() throws Exception {
		mvc.perform(post(reports(member("target"))).contentType("application/json").content("{\"reason\":\"사칭\"}"))
			.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"스팸 또는 광고", "부적절한 메시지", "욕설 또는 괴롭힘", "사칭", "기타"})
	void acceptsEveryAllowedReason(String reason) throws Exception {
		Member reporter = member("reporter");
		postAs(reporter, reports(member("target")), Map.of("reason", reason)).andExpect(status().isCreated()).andExpect(content().string(""));
		assertEquals(1, reportCount(reporter));
	}

	@Test void storesOnlyUserReportColumnsAsAwating() throws Exception {
		Member reporter = member("reporter");
		Member target = member("target");
		postAs(reporter, reports(target), Map.of("reason", "사칭")).andExpect(status().isCreated());

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM reports WHERE reporter_id = ?", reporter.getId());
		assertEquals(target.getId(), ((Number) column(row, "reported_user_id")).longValue());
		assertEquals("사칭", column(row, "reason"));
		assertEquals("AWATING", column(row, "status"));
		for (String empty : new String[]{"project_id", "post_id", "comment_id", "handled_by", "resolution", "handled_at"}) {
			assertNull(column(row, empty), empty);
		}
		assertEquals(LocalDateTime.of(2026, 11, 6, 10, 15), ((java.sql.Timestamp) column(row, "created_at")).toLocalDateTime());
	}

	// PostgreSQL은 컬럼 이름을 소문자로, H2는 대문자로 돌려준다.
	private static Object column(Map<String, Object> row, String name) {
		return row.containsKey(name) ? row.get(name) : row.get(name.toUpperCase());
	}

	@Test void rejectsSelfReport() throws Exception {
		Member me = member("me");
		postAs(me, reports(me), Map.of("reason", "기타")).andExpect(status().isBadRequest());
		assertEquals(0, reportCount(me));
	}

	@Test void rejectsUnknownTarget() throws Exception {
		postAs(member("me"), "/api/v1/users/no_such_tag/reports", Map.of("reason", "기타")).andExpect(status().isNotFound());
	}

	@Test void rejectsInvalidReason() throws Exception {
		Member me = member("me");
		Member target = member("target");
		postAs(me, reports(target), Map.of("reason", "없는 사유")).andExpect(status().isBadRequest());
		postAs(me, reports(target), Map.of()).andExpect(status().isBadRequest());
		postRawAs(me, reports(target), "{\"reason\":1}").andExpect(status().isBadRequest());
		assertEquals(0, reportCount(me));
	}

	@Test void allowsOneReportPerReporterAndTarget() throws Exception {
		Member me = member("me");
		Member target = member("target");
		postAs(me, reports(target), Map.of("reason", "사칭")).andExpect(status().isCreated());
		postAs(me, reports(target), Map.of("reason", "기타")).andExpect(status().isConflict());
		assertEquals(1, reportCount(me));

		postAs(me, reports(member("other")), Map.of("reason", "사칭")).andExpect(status().isCreated());
		postAs(member("someone"), reports(target), Map.of("reason", "사칭")).andExpect(status().isCreated());
	}

	@Test void suspendedMemberCanBeReported() throws Exception {
		Member me = member("me");
		Member target = member("target");
		suspend(target);
		postAs(me, reports(target), Map.of("reason", "욕설 또는 괴롭힘")).andExpect(status().isCreated());
	}
}
