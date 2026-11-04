package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.repository.MemberRepository;
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
class ProfileTagsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired ObjectMapper mapper;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("jwt.secret", () -> secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:profile-tags;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
    }
    String token(String email) { return "Bearer " + jwt.createAccessToken(email, "USER"); }
    String body(List<String> skills, List<String> interests) { return mapper.writeValueAsString(Map.of("skills", skills, "interests", interests)); }
    @Test
    void existingSignupValuesArePublicWithoutPrivateFieldsAndNullIsEmpty() throws Exception {
        var member = members.findById(1L).orElseThrow();
        member.updateProfileTags(List.of("Java", "React"), List.of("AI"));
        members.saveAndFlush(member);
        mvc.perform(get("/api/v1/users/tags-user/tags")).andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0]").value("Java")).andExpect(jsonPath("$.interests[0]").value("AI"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.id").doesNotExist());
        mvc.perform(get("/api/v1/users/other-user/tags")).andExpect(status().isOk())
                .andExpect(jsonPath("$.skills").isEmpty()).andExpect(jsonPath("$.interests").isEmpty());
    }
    @Test
    void updatePersistsNormalizedValuesAndDoesNotModifyOtherFieldsOrMembers() throws Exception {
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("tags@profile.test"))
                        .contentType("application/json").content(body(List.of(" Java ", "Java", "React"), List.of("AI", "AI"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(2));
        mvc.perform(get("/api/v1/users/tags-user/tags")).andExpect(jsonPath("$.skills[1]").value("React"))
                .andExpect(jsonPath("$.interests.length()").value(1));
        assertEquals(List.of("Java", "React"), members.findById(1L).orElseThrow().getSkills());
        assertNull(members.findById(2L).orElseThrow().getSkills());
        assertEquals("유지할 소개", jdbc.queryForObject("SELECT summary FROM users WHERE id=1", String.class));
        assertEquals("3년 차", jdbc.queryForObject("SELECT career FROM users WHERE id=1", String.class));
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("tags@profile.test"))
                .contentType("application/json").content(body(List.of(), List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills").isEmpty());
        assertEquals(List.of(), members.findById(1L).orElseThrow().getSkills());
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing", "warned-user", "suspended-user"})
    void unavailablePublicProfilesReturn404(String slug) throws Exception {
        mvc.perform(get("/api/v1/users/" + slug + "/tags")).andExpect(status().isNotFound());
    }
    @Test
    void privateRouteIsNotMatchedByPublicWildcardAndOnlyOwnerCanUpdate() throws Exception {
        mvc.perform(get("/api/v1/users/me/tags")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/tags").contentType("application/json").content(body(List.of(), List.of())))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("tags@profile.test"))
                        .contentType("application/json").content("{\"skills\":[\"Java\"],\"interests\":[],\"id\":2,\"email\":\"other@profile.test\"}"))
                .andExpect(status().isOk());
        assertNull(members.findById(2L).orElseThrow().getSkills());
    }
    @Test
    void warnedOwnerCanEditButSuspendedAndMissingOwnersCannot() throws Exception {
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("warned@profile.test"))
                .contentType("application/json").content(body(List.of("Java"), List.of())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/tags").header("Authorization", token("warned@profile.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills[0]").value("Java"));
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("suspended@profile.test"))
                .contentType("application/json").content(body(List.of(), List.of()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me/tags").header("Authorization", token("missing@profile.test")))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"skills\":null,\"interests\":[]}", "{\"skills\":[],\"interests\":null}",
            "{\"skills\":[\"Unknown\"],\"interests\":[]}", "{\"skills\":[],\"interests\":[\"unknown\"]}", "{\"skills\":[\" \"],\"interests\":[]}"})
    void invalidValuesNeverOverwriteData(String json) throws Exception {
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("tags@profile.test"))
                .contentType("application/json").content(json)).andExpect(status().isBadRequest());
        assertNull(members.findById(1L).orElseThrow().getSkills());
    }
    @Test
    void tooManyValuesAreRejected() throws Exception {
        mvc.perform(put("/api/v1/users/me/tags").header("Authorization", token("tags@profile.test"))
                .contentType("application/json").content(body(java.util.Collections.nCopies(31, "Java"), List.of())))
                .andExpect(status().isBadRequest());
    }
}
