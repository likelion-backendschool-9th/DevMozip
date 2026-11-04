package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.repository.MemberRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM users",
        "INSERT INTO users (id, job_categories, email, nickname, tag, status, role, summary, career, trust_score, profile_image, password_hash, contact_hours) VALUES (1,'[\"백엔드 개발자\"]' FORMAT JSON,'owner@profile.test','원래닉네임','owner-tag','ACTIVE','USER','원래 소개','1년 차',38.50,'/keep.png','unchanged-hash','기존 연락 시간')",
        "INSERT INTO users (id, job_categories, email, nickname, tag, status, role, career, trust_score) VALUES (2,'[\"백엔드 개발자\"]' FORMAT JSON,'other@profile.test','OtherName','other-tag','ACTIVE','USER','2년 차',36.50)",
        "INSERT INTO users (id, job_categories, email, nickname, tag, status, role, career, trust_score) VALUES (3,'[\"백엔드 개발자\"]' FORMAT JSON,'suspended@profile.test','정지회원','suspended-tag','SUSPENDED','USER','신입 / 학습 중',36.50)"
})
class ProfileUpdateIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.devmozip.backend.security.jwt.RefreshTokenStore refreshTokenStore;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String password = UUID.randomUUID().toString();
        registry.add("jwt.secret", () -> password + password);
        registry.add("auth.bootstrap.user-password", () -> password);
        registry.add("auth.bootstrap.admin-password", () -> password);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:profile-update;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    @Test
    void ownProfileAndJobChoicesUseAuthenticatedEmail() throws Exception {
        mvc.perform(get("/api/v1/users/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/profile/job-categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", token("owner@profile.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/v1/users/me/profile/job-categories").header("Authorization", token("owner@profile.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("프론트엔드 개발자"))
                .andExpect(jsonPath("$[1]").value("백엔드 개발자"));
    }

    @Test
    void ownUnassignedJobCanBeLoadedForEditing() throws Exception {
        jdbc.update("UPDATE users SET job_categories = NULL WHERE id = 1");
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", token("owner@profile.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobCategories").isEmpty());
        mvc.perform(get("/api/v1/users/owner-tag")).andExpect(status().isNotFound());
    }

    @Test
    void loginTokenCanIdentifyOwnerAndUpdatePublicProfile() throws Exception {
        String password = UUID.randomUUID().toString();
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = 1", passwordEncoder.encode(password));
        var login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username", "owner@profile.test", "password", password))))
                .andExpect(status().isOk()).andReturn();
        String accessToken = mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
        String authorization = "Bearer " + accessToken;
        mvc.perform(get("/api/v1/users/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner@profile.test"))
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(put("/api/v1/users/me/profile").header("Authorization", authorization)
                .contentType("application/json").content(mapper.writeValueAsString(valid())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/v1/users/updated-tag"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("수정닉네임"));
    }

    @Test
    void authenticatedEmailUpdatesOnlyOwnerAndPersists() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile").header("Authorization", token("owner@profile.test"))
                .contentType("application/json").content(mapper.writeValueAsString(valid())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nickname").value("수정닉네임"))
                .andExpect(jsonPath("$.jobCategories[0]").value("프론트엔드 개발자"));
        var saved = members.findByEmail("owner@profile.test").orElseThrow();
        assertEquals("수정닉네임", saved.getNickname());
        assertEquals("updated-tag", saved.getTag());
        assertEquals(java.util.List.of("프론트엔드 개발자"), saved.getJobCategories());
        assertEquals("2년 차", saved.getCareer());
        assertEquals("기존 연락 시간", saved.getContactHours());
        assertEquals("수정 소개", saved.getSummary());
        assertEquals("RESTING", saved.getCollaborationStatus().name());
        assertEquals("USER", saved.getRole().name());
        assertEquals("/keep.png", saved.getProfileImage());
        assertEquals("unchanged-hash", saved.getPasswordHash());
        assertEquals("OtherName", members.findById(2L).orElseThrow().getNickname());
        mvc.perform(get("/api/v1/users/updated-tag")).andExpect(status().isOk())
                .andExpect(jsonPath("$.career").value("2년 차"));
    }

    @Test
    void savesContactHoursAndReturnsThemOnPublicRead() throws Exception {
        var body = valid(); body.put("contactHours", "  평일 19:00~22:00 · 주말 협의  ");
        mvc.perform(put("/api/v1/users/me/profile").header("Authorization", token("owner@profile.test"))
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contactHours").value("평일 19:00~22:00 · 주말 협의"));
        assertEquals("평일 19:00~22:00 · 주말 협의", members.findById(1L).orElseThrow().getContactHours());
        assertNull(members.findById(2L).orElseThrow().getContactHours());
        mvc.perform(get("/api/v1/users/updated-tag")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactHours").value("평일 19:00~22:00 · 주말 협의"));
    }

    @Test
    void nullContactHoursPreservesCurrentValue() throws Exception {
        var body = valid(); body.put("contactHours", null);
        perform(body, "owner@profile.test", 200);
        assertEquals("기존 연락 시간", members.findById(1L).orElseThrow().getContactHours());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void explicitBlankClearsContactHours(String contact) throws Exception {
        var body = valid(); body.put("contactHours", contact);
        perform(body, "owner@profile.test", 200);
        assertNull(members.findById(1L).orElseThrow().getContactHours());
        mvc.perform(get("/api/v1/users/updated-tag")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactHours").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void acceptsOneHundredCharactersAndRejectsOneHundredOne() throws Exception {
        var body = valid(); body.put("contactHours", "가".repeat(101));
        perform(body, "owner@profile.test", 400);
        assertUnchanged();
        body.put("contactHours", "가".repeat(100));
        perform(body, "owner@profile.test", 200);
        assertEquals("가".repeat(100), members.findById(1L).orElseThrow().getContactHours());
    }

    @Test
    void retainingOwnNicknameAndTagDoesNotConflict() throws Exception {
        var body = valid(); body.put("nickname", "원래닉네임"); body.put("tag", "owner-tag");
        perform(body, "owner@profile.test", 200);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"신입 / 학습 중", "2년 차"})
    void persistsCareerTextAndNull(String career) throws Exception {
        var body = valid(); body.put("career", career); body.put("summary", null); body.put("collaborationStatus", null);
        perform(body, "owner@profile.test", 200);
        var saved = members.findById(1L).orElseThrow();
        assertEquals(career, saved.getCareer());
        assertNull(saved.getSummary());
        assertNull(saved.getCollaborationStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nickname", "tag"})
    void rejectsOtherMembersValuesCaseInsensitively(String field) throws Exception {
        var body = valid(); body.put(field, field.equals("nickname") ? "othername" : "OTHER-TAG");
        perform(body, "owner@profile.test", 409);
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"too-long-career", "reserved-tag", "missing-job", "blank-nickname", "invalid-status"})
    void invalidInputDoesNotPersistPartialChanges(String failure) throws Exception {
        var body = valid();
        switch (failure) {
            case "too-long-career" -> body.put("career", "가".repeat(31));
            case "reserved-tag" -> body.put("tag", "ME");
            case "missing-job" -> body.put("jobCategories", java.util.List.of("없는 직군"));
            case "blank-nickname" -> body.put("nickname", "  ");
            default -> body.put("collaborationStatus", "INVALID");
        }
        perform(body, "owner@profile.test", 400);
        assertUnchanged();
    }

    @Test
    void databaseConstraintFailureRollsBackAllChangedFields() throws Exception {
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT profile_summary_test CHECK (summary IS NULL OR summary <> 'db-rejected')");
        try {
            var body = valid(); body.put("summary", "db-rejected"); body.put("contactHours", "저장되지 않을 값");
            perform(body, "owner@profile.test", 409);
            assertUnchanged();
            assertEquals("원래 소개", members.findById(1L).orElseThrow().getSummary());
        } finally {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT profile_summary_test");
        }
    }

    @Test
    void anonymousCannotUpdateProfile() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile").contentType("application/json").content(mapper.writeValueAsString(valid())))
                .andExpect(status().isUnauthorized());
        assertUnchanged();
    }

    @Test
    void mePathCannotBecomeAnonymousPublicProfile() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenCannotUpdateProfile() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile").header("Authorization", "Bearer invalid")
                .contentType("application/json").content(mapper.writeValueAsString(valid())))
                .andExpect(status().isUnauthorized());
        assertUnchanged();
    }

    @Test
    void unknownAuthenticatedEmailIsNotReplacedByDemoUser() throws Exception {
        perform(valid(), "missing@profile.test", 404); assertUnchanged();
    }

    @Test
    void suspendedMemberCannotUpdate() throws Exception {
        perform(valid(), "suspended@profile.test", 403);
        assertEquals("정지회원", members.findById(3L).orElseThrow().getNickname());
    }

    @Test
    void bodyCannotSelectOtherUserOrModifyPrivilegedFields() throws Exception {
        var body = valid(); body.put("id", 2); body.put("email", "other@profile.test");
        body.put("role", "ADMIN"); body.put("trustScore", 100);
        var response = mvc.perform(put("/api/v1/users/me/profile").header("Authorization", token("owner@profile.test"))
                .contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().getResponse();
        // Jackson의 알 수 없는 필드 거부/무시 정책과 무관하게 수정 대상·권한은 변경될 수 없다.
        assertTrue(response.getStatus() == 200 || response.getStatus() == 400);
        assertEquals("OtherName", members.findById(2L).orElseThrow().getNickname());
        var owner = members.findById(1L).orElseThrow();
        assertEquals("owner@profile.test", owner.getEmail());
        assertEquals("USER", owner.getRole().name());
        assertEquals(0, new java.math.BigDecimal("38.50").compareTo(owner.getTrustScore()));
    }

    private void perform(Map<String, Object> body, String email, int status) throws Exception {
        mvc.perform(put("/api/v1/users/me/profile").header("Authorization", token(email))
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().is(status));
    }

    private String token(String email) { return "Bearer " + jwt.createAccessToken(email, "USER"); }

    private Map<String, Object> valid() {
        var body = new LinkedHashMap<String, Object>();
        body.put("nickname", "수정닉네임"); body.put("tag", "updated-tag"); body.put("jobCategories", java.util.List.of("프론트엔드 개발자"));
        body.put("summary", "수정 소개"); body.put("career", "2년 차"); body.put("collaborationStatus", "RESTING");
        return body;
    }

    private void assertUnchanged() {
        var owner = members.findById(1L).orElseThrow();
        assertEquals("원래닉네임", owner.getNickname());
        assertEquals("owner-tag", owner.getTag());
        assertEquals("1년 차", owner.getCareer());
        assertEquals("기존 연락 시간", owner.getContactHours());
        assertEquals(java.util.List.of("백엔드 개발자"), owner.getJobCategories());
    }
}
