package com.devmozip.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
// Docker를 찾지 못하면 실패 대신 건너뛴다. (CI의 ubuntu-latest에는 Docker가 있어 실제로 실행된다)
@Testcontainers(disabledWithoutDocker = true)
class BackendApplicationTests {

	// 운영(compose)과 같은 이미지로 띄운다. @ServiceConnection이 접속 정보를 datasource에 연결한다
	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Test
	void contextLoads() {
	}

}
