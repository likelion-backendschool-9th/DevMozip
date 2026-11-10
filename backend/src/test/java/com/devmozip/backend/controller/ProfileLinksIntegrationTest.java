package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.service.AccountWithdrawalTasks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM users",
        "INSERT INTO users (id,email,nickname,tag,status,role,summary,career) VALUES (1,'tags@profile.test','태그회원','tags-user','ACTIVE','USER','유지할 소개','3년 차')",
        "INSERT INTO users (id,email,nickname,tag,status,role) VALUES (2,'other@profile.test','다른회원','other-user','ACTIVE','USER')",
        "INSERT INTO users (id,email,nickname,tag,status,role) VALUES (3,'warned@profile.test','경고회원','warned-user','WARNED','USER')",
        "INSERT INTO users (id,email,nickname,tag,status,role) VALUES (4,'suspended@profile.test','정지회원','suspended-user','SUSPENDED','USER')"
})
class ProfileLinksIntegrationTest {
    // Redis 없이 실행한다. DB에 없는 회원의 토큰은 accountId 클레임이 없어 JwtProvider가 탈퇴 토큰 목록(Redis)을 확인하므로 대신한다
    @MockitoBean AccountWithdrawalTasks withdrawalTasks;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired ObjectMapper mapper;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("jwt.secret", () -> secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:profile-links;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
    }
    String token(String email) { return "Bearer " + jwt.createAccessToken(email, "USER"); }

    Map<String, Object> valid() {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("github", "https://github.com/devmozip"); value.put("blog", "");
        value.put("externalLinks", List.of(Map.of("label", " 포트폴리오 ", "url", "https://example.com/work")));
        return value;
    }
    void save(Map<String, Object> value, String email, int status) throws Exception {
        mvc.perform(put("/api/v1/users/me/links").header("Authorization", token(email))
                .contentType("application/json").content(mapper.writeValueAsString(value)))
                .andExpect(status().is(status));
    }
    @Test
    void persistsLinksAndReadsPubliclyWithoutChangingOtherFields() throws Exception {
        save(valid(), "tags@profile.test", 200);
        mvc.perform(get("/api/v1/users/tags-user/links")).andExpect(status().isOk())
                .andExpect(jsonPath("$.github").value("https://github.com/devmozip"))
                .andExpect(jsonPath("$.externalLinks[0].label").value("포트폴리오"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.id").doesNotExist());
        String stored = members.findById(1L).orElseThrow().getExternalLinks();
        assertTrue(stored.contains("https://example.com/work"));
        assertEquals("유지할 소개", jdbc.queryForObject("SELECT summary FROM users WHERE id=1", String.class));
        assertEquals("3년 차", members.findById(1L).orElseThrow().getCareer());
        assertNull(members.findById(2L).orElseThrow().getExternalLinks());
        mvc.perform(get("/api/v1/users/me/links").header("Authorization", token("tags@profile.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.externalLinks.length()").value(1));
    }
    @Test
    void nullIsEmptyAndExplicitClearPersists() throws Exception {
        mvc.perform(get("/api/v1/users/tags-user/links")).andExpect(status().isOk())
                .andExpect(jsonPath("$.github").value("")).andExpect(jsonPath("$.externalLinks").isEmpty());
        save(valid(), "tags@profile.test", 200);
        save(Map.of("github", "", "blog", "", "externalLinks", List.of()), "tags@profile.test", 200);
        mvc.perform(get("/api/v1/users/tags-user/links")).andExpect(jsonPath("$.github").value(""))
                .andExpect(jsonPath("$.externalLinks").isEmpty());
    }
    @Test
    void ownRoutesAreProtectedBeforePublicWildcardAndBodyCannotSelectOwner() throws Exception {
        mvc.perform(get("/api/v1/users/me/links")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/links").contentType("application/json").content(mapper.writeValueAsString(valid())))
                .andExpect(status().isUnauthorized());
        var value = valid(); value.put("id", 2); value.put("email", "other@profile.test");
        save(value, "tags@profile.test", 200);
        assertNull(members.findById(2L).orElseThrow().getExternalLinks());
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing", "warned-user", "suspended-user"})
    void unavailableProfilesUse404(String slug) throws Exception {
        mvc.perform(get("/api/v1/users/" + slug + "/links")).andExpect(status().isNotFound());
    }
    @Test
    void accountStatusPolicyMatchesOtherProfileSections() throws Exception {
        save(valid(), "warned@profile.test", 200);
        mvc.perform(get("/api/v1/users/me/links").header("Authorization", token("warned@profile.test")))
                .andExpect(status().isOk());
        save(valid(), "suspended@profile.test", 403);
        save(valid(), "missing@profile.test", 404);
    }
    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,test", "//example.com", "/relative", "https://user:pass@example.com", "https://example.com:99999", "https://", "https://example.com/a b"})
    void rejectsUnsafeUrlsWithoutOverwritingExistingData(String url) throws Exception {
        save(valid(), "tags@profile.test", 200);
        String before = members.findById(1L).orElseThrow().getExternalLinks();
        var value = valid(); value.put("github", url); save(value, "tags@profile.test", 400);
        value = valid(); value.put("externalLinks", List.of(Map.of("label", "링크", "url", url)));
        save(value, "tags@profile.test", 400);
        assertEquals(before, members.findById(1L).orElseThrow().getExternalLinks());
    }
    @Test
    void validatesMissingNullCountAndLengthLimits() throws Exception {
        for (String key : List.of("github", "blog", "externalLinks")) {
            var missing = valid(); missing.remove(key); save(missing, "tags@profile.test", 400);
            var absent = valid(); absent.put(key, null); save(absent, "tags@profile.test", 400);
        }
        var value = valid(); value.put("externalLinks", java.util.Collections.nCopies(11, Map.of("label", "링크", "url", "https://example.com")));
        save(value, "tags@profile.test", 400);
        value.put("externalLinks", List.of(Map.of("label", " ", "url", "https://example.com"))); save(value, "tags@profile.test", 400);
        value.put("externalLinks", List.of(Map.of("label", "가".repeat(41), "url", "https://example.com"))); save(value, "tags@profile.test", 400);
        value = valid(); value.put("github", "https://example.com/" + "a".repeat(482)); save(value, "tags@profile.test", 400);
        value.put("github", "https://example.com/" + "a".repeat(480)); save(value, "tags@profile.test", 200);
        value = valid(); value.put("externalLinks", java.util.Collections.nCopies(10, Map.of("label", "가".repeat(40), "url", "https://example.com"))); save(value, "tags@profile.test", 200);
    }
    @Test
    void legacyUnknownOrUnsafeJsonIsNotReportedAsEmptyOrMutated() throws Exception {
        for (String data : List.of("[]", "{\"github\":\"javascript:alert(1)\",\"blog\":\"\",\"externalLinks\":[]}", "{\"unknown\":\"legacy\"}")) {
            var member = members.findById(1L).orElseThrow(); member.updateExternalLinks(data); members.saveAndFlush(member);
            mvc.perform(get("/api/v1/users/tags-user/links")).andExpect(status().isInternalServerError());
            assertEquals(data, members.findById(1L).orElseThrow().getExternalLinks());
        }
    }
}
