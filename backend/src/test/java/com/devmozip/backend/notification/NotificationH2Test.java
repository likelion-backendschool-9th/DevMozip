package com.devmozip.backend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.enums.NotificationType;
import com.devmozip.backend.repository.NotificationQueryRepository;
import com.devmozip.backend.repository.NotificationQueryRepository.Filter;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.NotificationDraft;
import com.devmozip.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** local 프로필과 같은 H2 설정에서 알림 생성(중복 무시)·목록·읽음 SQL이 동작하는지 확인한다. Docker가 없어도 실행된다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:notification-api",
	"spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationH2Test {
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired NotificationService service;
	@Autowired NotificationQueryRepository queries;

	@Test
	void createListReadAndReadAllWorkOnH2() throws Exception {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM users");
		jdbc.update("INSERT INTO users(id, email, role, status) VALUES (1, 'h2-notice@test.local', 'USER', 'ACTIVE')");
		var draft = new NotificationDraft(1, null, null, "h2-token", NotificationType.PROJECT_STATUS_CHANGED, "프로젝트 상태 변경", "본문", "/projects/1");
		assertTrue(service.create(draft));
		assertFalse(service.create(draft));
		assertTrue(service.create(new NotificationDraft(1, null, null, "h2-other", NotificationType.PROJECT_COMPLETED, "프로젝트 종료", "본문", "/projects/1")));

		var now = LocalDateTime.now();
		assertEquals(2, queries.find(1, Filter.UNREAD, 0, 20, now).rows().size());
		assertEquals(0, queries.find(1, Filter.READ, 0, 20, now).rows().size());
		assertTrue(queries.findReviewAnchor(1, 1).isEmpty());

		String token = "Bearer " + jwt.createAccessToken("h2-notice@test.local", "USER");
		long id = jdbc.queryForObject("SELECT id FROM notifications WHERE token = 'h2-token'", Long.class);
		mvc.perform(get("/api/v1/notifications").header("Authorization", token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.unreadCount").value(2));
		mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", token))
			.andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
		mvc.perform(get("/api/v1/notifications").param("status", "read").header("Authorization", token))
			.andExpect(jsonPath("$.items[0].id").value(id));
		mvc.perform(patch("/api/v1/notifications/read-all").header("Authorization", token))
			.andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
	}
}
