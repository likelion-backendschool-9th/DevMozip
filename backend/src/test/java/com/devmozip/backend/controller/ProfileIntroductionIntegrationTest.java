package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM users",
        "INSERT INTO users (id,email,nickname,tag,status,role,summary,introduction,introduction_format) VALUES (1,'intro@profile.test','소개회원','intro-tag','ACTIVE','USER','유지할 한 줄 소개','기존 상세 소개','text')",
        "INSERT INTO users (id,email,nickname,tag,status,role,introduction) VALUES (2,'other@profile.test','다른회원','other-tag','ACTIVE','USER','다른 회원 소개')",
        "INSERT INTO users (id,email,nickname,tag,status,role) VALUES (3,'warned@profile.test','경고회원','warned-tag','WARNED','USER')",
        "INSERT INTO users (id,email,nickname,tag,status,role) VALUES (4,'suspended@profile.test','정지회원','suspended-tag','SUSPENDED','USER')"
})
class ProfileIntroductionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("jwt.secret", () -> secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:profile-introduction;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
    }
    String token(String email) { return "Bearer " + jwt.createAccessToken(email, "USER"); }
    String body(String introduction, String format) { return mapper.writeValueAsString(Map.of("introduction", introduction, "format", format)); }

    @Test
    void publicReadIsAnonymousAndDoesNotExposeMemberFields() throws Exception {
        mvc.perform(get("/api/v1/users/intro-tag/introduction"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.introduction").value("기존 상세 소개"))
                .andExpect(jsonPath("$.format").value("text"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.id").doesNotExist());
        mvc.perform(get("/api/v1/users/other-tag/introduction"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.format").value("text"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing", "warned-tag", "suspended-tag"})
    void unavailableAccountsUseSamePublic404(String slug) throws Exception {
        mvc.perform(get("/api/v1/users/" + slug + "/introduction"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }
    @Test
    void ownRoutesRequireAuthenticationBeforePublicWildcard() throws Exception {
        mvc.perform(get("/api/v1/users/me/introduction")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/introduction").contentType("application/json").content(body("소개", "text")))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void ownerUpdatePersistsWithoutChangingSummaryOrOtherMember() throws Exception {
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content(body("첫 줄\n둘째 줄 <script>일반 텍스트</script>", "text")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.format").value("text"));
        mvc.perform(get("/api/v1/users/intro-tag/introduction"))
                .andExpect(jsonPath("$.introduction").value("첫 줄\n둘째 줄 <script>일반 텍스트</script>"));
        assertEquals("유지할 한 줄 소개", jdbc.queryForObject("SELECT summary FROM users WHERE id=1", String.class));
        assertEquals("다른 회원 소개", jdbc.queryForObject("SELECT introduction FROM users WHERE id=2", String.class));
    }
    @Test
    void htmlIsSanitizedBeforePersistenceAndKeepsAllowedFormatting() throws Exception {
        String html = "<h2>소개</h2><p style='color: red; position: fixed' onclick='alert(1)'><strong>개발자</strong></p>"
                + "<script>alert(1)</script><iframe src='https://example.com'></iframe><a href='javascript:alert(1)'>링크</a>";
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content(body(html, "html"))).andExpect(status().isOk());
        String saved = jdbc.queryForObject("SELECT introduction FROM users WHERE id=1", String.class);
        assertTrue(saved.contains("<h2>소개</h2>")); assertTrue(saved.contains("<strong>개발자</strong>"));
        assertTrue(saved.contains("color:"));
        for (String removed : new String[]{"<script", "<iframe", "onclick", "javascript:", "position:"}) assertFalse(saved.contains(removed), saved);
        mvc.perform(get("/api/v1/users/intro-tag/introduction")).andExpect(jsonPath("$.format").value("html"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "<script>alert(1)</script>"})
    void explicitEmptyOrRemovedHtmlClearsIntroduction(String value) throws Exception {
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content(body(value, "html")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.introduction").isEmpty())
                .andExpect(jsonPath("$.format").value("text"));
        assertNull(jdbc.queryForObject("SELECT introduction FROM users WHERE id=1", String.class));
    }
    @Test
    void maxLengthBoundaryAndInvalidInputDoNotOverwriteData() throws Exception {
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content(body("가".repeat(20000), "text"))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content(body("가".repeat(20001), "text")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROFILE_INVALID_REQUEST"));
        assertEquals(20000, jdbc.queryForObject("SELECT introduction FROM users WHERE id=1", String.class).length());
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"introduction\":null,\"format\":\"text\"}", "{\"introduction\":\"값\",\"format\":\"markdown\"}", "{\"introduction\":\"값\",\"format\":null}"})
    void missingNullAndUnknownFormatAreRejected(String json) throws Exception {
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content(json)).andExpect(status().isBadRequest());
        assertEquals("기존 상세 소개", jdbc.queryForObject("SELECT introduction FROM users WHERE id=1", String.class));
    }
    @Test
    void warnedOwnerCanEditButIsNotPublicAndSuspendedOwnerIsDenied() throws Exception {
        mvc.perform(get("/api/v1/users/me/introduction").header("Authorization", token("warned@profile.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.introduction").isEmpty());
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("warned@profile.test"))
                        .contentType("application/json").content(body("경고 계정 소개", "text"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/warned-tag/introduction")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("suspended@profile.test"))
                        .contentType("application/json").content(body("수정", "text"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me/introduction").header("Authorization", token("missing@profile.test")))
                .andExpect(status().isNotFound());
    }
    @Test
    void clientCannotChooseAnotherOwnerAndLegacyHtmlIsFilteredOnRead() throws Exception {
        mvc.perform(put("/api/v1/users/me/introduction").header("Authorization", token("intro@profile.test"))
                        .contentType("application/json").content("{\"introduction\":\"내 소개\",\"format\":\"text\",\"id\":2,\"email\":\"other@profile.test\"}"))
                .andExpect(status().isOk());
        assertEquals("다른 회원 소개", jdbc.queryForObject("SELECT introduction FROM users WHERE id=2", String.class));
        jdbc.update("UPDATE users SET introduction=?, introduction_format='html' WHERE id=1", "<p>기존 값</p><script>alert(1)</script>");
        var response = mvc.perform(get("/api/v1/users/intro-tag/introduction")).andExpect(status().isOk()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("<script"));
    }
}
