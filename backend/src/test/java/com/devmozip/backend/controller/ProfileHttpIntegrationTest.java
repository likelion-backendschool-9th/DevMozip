package com.devmozip.backend.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

// 실제 HTTP 서버 → 보안 필터 → Controller → Service → JPA → H2를 검증한다.
// 운영 PostgreSQL·Redis 연결 및 테스트 대역 Repository는 사용하지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = {
        "DELETE FROM users",
        "INSERT INTO users (id, job_categories, tag, nickname, status, summary, profile_image, banner_image, collaboration_status, trust_score, career, contact_hours) VALUES (1, '[\"백엔드 개발자\"]' FORMAT JSON, 'profile-http-active', '통합 테스트 개발자', 'ACTIVE', '같이 개발해요', '/profile.png', '/banner.png', 'AVAILABLE', 36.50, '2년 차', '평일 19:00~22:00')",
        "INSERT INTO users (id, job_categories, tag, nickname, status, trust_score) VALUES (2, '[\"백엔드 개발자\"]' FORMAT JSON, 'profile-http-locked', '잠금 테스트', 'WARNED', 36.50)",
        "INSERT INTO users (id, job_categories, tag, nickname, status, trust_score) VALUES (3, '[\"백엔드 개발자\"]' FORMAT JSON, 'profile-http-suspended', '제재 테스트', 'SUSPENDED', 36.50)",
        "INSERT INTO users (id, job_categories, tag, nickname, status, trust_score) VALUES (4, '[\"백엔드 개발자\"]' FORMAT JSON, 'profile-http-minimal', '기본 테스트', 'ACTIVE', 36.50)",
        "INSERT INTO users (id, job_categories, tag, nickname, status, trust_score, career) VALUES (5, '[\"백엔드 개발자\"]' FORMAT JSON, 'profile-http-junior', '신입 테스트', 'ACTIVE', 36.50, '신입 / 학습 중')"
})
class ProfileHttpIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper mapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void localProperties(DynamicPropertyRegistry registry) {
        String database = "jdbc:h2:mem:profile-" + UUID.randomUUID() + ";MODE=PostgreSQL";
        String password = UUID.randomUUID().toString();
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("spring.datasource.url", () -> database);
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("jwt.secret", () -> secret);
        registry.add("auth.bootstrap.user-password", () -> password);
        registry.add("auth.bootstrap.admin-password", () -> password);
        registry.add("management.health.redis.enabled", () -> "false");
    }

    @Test
    void readsPersistedProfileOverHttpWithoutLogin() throws Exception {
        var response = get("/api/v1/users/profile-http-active");
        assertEquals(200, response.statusCode());
        var json = mapper.readTree(response.body());
        assertEquals(1L, json.get("id").asLong());
        assertEquals("profile-http-active", json.get("tag").asString());
        assertEquals("통합 테스트 개발자", json.get("nickname").asString());
        assertEquals("백엔드 개발자", json.get("jobCategories").get(0).asString());
        assertEquals("같이 개발해요", json.get("summary").asString());
        assertEquals("2년 차", json.get("career").asString());
        assertEquals("평일 19:00~22:00", json.get("contactHours").asString());
        assertEquals("/profile.png", json.get("profileImage").asString());
        assertEquals("/banner.png", json.get("bannerImage").asString());
        assertEquals("AVAILABLE", json.get("collaborationStatus").asString());
        assertEquals(0, new BigDecimal("36.50").compareTo(json.get("trustScore").decimalValue()));
        Set<String> fields = new HashSet<>();
        json.propertyNames().forEach(fields::add);
        assertEquals(Set.of("id", "tag", "nickname", "jobCategories", "summary", "profileImage",
                "bannerImage", "collaborationStatus", "trustScore", "career", "contactHours"), fields);
    }

    @Test
    void optionalFieldsCanBeNullInDatabase() throws Exception {
        var response = get("/api/v1/users/profile-http-minimal");
        assertEquals(200, response.statusCode());
        var json = mapper.readTree(response.body());
        for (String field : Set.of("summary", "profileImage", "bannerImage", "collaborationStatus", "career", "contactHours")) {
            assertTrue(json.get(field).isNull(), field);
        }
    }

    @Test
    void careerTextIsReturnedAsStored() throws Exception {
        var response = get("/api/v1/users/profile-http-junior");
        assertEquals(200, response.statusCode());
        var career = mapper.readTree(response.body()).get("career");
        assertEquals("신입 / 학습 중", career.asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile-http-missing", "profile-http-locked", "profile-http-suspended"})
    void absentAndInactiveProfilesReturnSame404(String tag) throws Exception {
        var response = get("/api/v1/users/" + tag);
        assertEquals(404, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/problem+json"));
        var json = mapper.readTree(response.body());
        assertEquals("PROFILE_NOT_FOUND", json.get("code").asString());
        assertEquals("프로필을 찾을 수 없습니다.", json.get("detail").asString());
    }

    @Test
    void privateRouteStillRequiresAuthentication() throws Exception {
        var response = get("/api/private/profile-http-check");
        assertEquals(401, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
