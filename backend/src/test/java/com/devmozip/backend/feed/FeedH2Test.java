package com.devmozip.backend.feed;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * local 프로필과 같은 H2 설정에서 피드 매핑·목록 SQL과 API가 PostgreSQL과 같게 동작하는지 확인한다. Docker가 없어도 실행된다.
 * 세 묶음을 @Nested로 묶고, 쓴 컨텍스트는 바로 닫는다(@DirtiesContext, 이유는 FeedPostgresIntegrationTest).
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:feed-h2",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FeedH2Test {
	@Nested
	class Repository extends PostRepositoryTests {
		@Test
		void createsJsonColumnsAsH2Json() {
			for (String column : new String[]{"TECH_STACKS", "INTERESTS", "IMAGE_URLS"}) {
				assertEquals("JSON", jdbc.queryForObject("SELECT data_type FROM information_schema.columns"
					+ " WHERE table_name = 'POSTS' AND column_name = ?", String.class, column), column);
			}
		}
	}

	@Nested
	class Posts extends FeedPostApiTests {
	}

	@Nested
	class Interactions extends FeedInteractionApiTests {
	}
}
