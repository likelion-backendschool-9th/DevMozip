package com.devmozip.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InMemoryRefreshTokenStore;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AuthService;
import com.devmozip.backend.service.PasswordResetMailSender;
import com.devmozip.backend.service.PasswordResetService;
import com.devmozip.backend.service.impl.InMemoryPasswordResetService;
import com.jayway.jsonpath.JsonPath;

/**
 * 로그인한 회원의 비밀번호 변경을 실제 보안 설정·H2와 함께 확인한다.
 * 저장소는 메모리 구현이므로 실제 Redis 검증이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeIntegrationTest {

    private static final String URL = "/api/v1/users/me/password";
    private static final String CURRENT_PASSWORD = "Devmozip123";
    private static final String NEW_PASSWORD = "Changed456";

    @TestConfiguration
    static class StoreConfiguration {
        @Bean
        @Primary
        RefreshTokenStore inMemoryRefreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }

        @Bean
        @Primary
        PasswordResetService inMemoryPasswordResetService(PasswordResetProperties properties) {
            return new InMemoryPasswordResetService(properties);
        }
    }

    @TestBean(name = "passwordResetMailExecutor", methodName = "syncExecutor")
    TaskExecutor mailExecutor;

    static TaskExecutor syncExecutor() {
        return new SyncTaskExecutor();
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoSpyBean RefreshTokenStore refreshTokenStore;
    @MockitoBean PasswordResetMailSender mailSender;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString();
        registry.add("jwt.secret", () -> secret + secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:password-change;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        when(mailSender.isAvailable()).thenReturn(true);
    }

    private String saveMember(String password, String kakaoId) {
        String email = UUID.randomUUID() + "@devmozip.test";
        members.saveAndFlush(Member.builder()
                .email(email)
                .passwordHash(password != null ? passwordEncoder.encode(password) : null)
                .kakaoId(kakaoId)
                .nickname(UUID.randomUUID().toString().substring(0, 20))
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String loginBody(String email, String password) throws Exception {
        return login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions change(String accessToken, String body) throws Exception {
        return mvc.perform(patch(URL).header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String currentPassword, String newPassword) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                .formatted(currentPassword, newPassword, newPassword);
    }

    private String accessToken(String email) throws Exception {
        return JsonPath.read(loginBody(email, CURRENT_PASSWORD), "$.accessToken");
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 변경하면_새_비밀번호로_로그인되고_이전_비밀번호는_401이다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);

        change(accessToken(email), body(CURRENT_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, CURRENT_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 변경하면_기존_리프레시_토큰으로_재발급할_수_없다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);
        String tokens = loginBody(email, CURRENT_PASSWORD);
        String refreshToken = JsonPath.read(tokens, "$.refreshToken");
        refresh(refreshToken).andExpect(status().isOk());

        change(JsonPath.read(tokens, "$.accessToken"), body(CURRENT_PASSWORD, NEW_PASSWORD)).andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void 변경하면_쓰지_않은_재설정_링크가_무효가_된다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);
        mvc.perform(post("/api/v1/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendResetLink(eq(email), token.capture());

        change(accessToken(email), body(CURRENT_PASSWORD, NEW_PASSWORD)).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"Another789\",\"confirmPassword\":\"Another789\"}".formatted(token.getValue())))
                .andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호를_빼고_보내면_400이고_바뀌지_않는다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);

        change(accessToken(email), "{\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현재 비밀번호를 입력해 주세요."));

        login(email, CURRENT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호가_틀려도_세션이_유지되고_바뀌지_않는다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);
        String tokens = loginBody(email, CURRENT_PASSWORD);

        change(JsonPath.read(tokens, "$.accessToken"), body("Wrong1234", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않습니다."));

        refresh(JsonPath.read(tokens, "$.refreshToken")).andExpect(status().isOk());
        login(email, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 새_비밀번호가_현재와_같으면_400이고_세션이_유지된다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);
        String tokens = loginBody(email, CURRENT_PASSWORD);

        change(JsonPath.read(tokens, "$.accessToken"), body(CURRENT_PASSWORD, CURRENT_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현재 비밀번호와 다른 값을 입력해 주세요."));

        refresh(JsonPath.read(tokens, "$.refreshToken")).andExpect(status().isOk());
    }

    @Test
    void Redis가_실패하면_변경도_롤백된다() throws Exception {
        String email = saveMember(CURRENT_PASSWORD, null);
        String accessToken = accessToken(email);
        doThrow(new IllegalStateException("redis down")).when(refreshTokenStore).remove(email);

        change(accessToken, body(CURRENT_PASSWORD, NEW_PASSWORD)).andExpect(status().isInternalServerError());

        login(email, CURRENT_PASSWORD).andExpect(status().isOk());
        login(email, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 카카오_전용_회원은_현재_비밀번호_없이_첫_비밀번호를_정하고_세션이_유지된다() throws Exception {
        String email = saveMember(null, "kakao-" + UUID.randomUUID());
        LoginResponse tokens = authService.issueTokens(members.findByEmail(email).orElseThrow());

        change(tokens.accessToken(), "{\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isOk());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }
}
