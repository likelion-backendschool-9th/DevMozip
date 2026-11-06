package com.devmozip.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InMemoryRefreshTokenStore;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.PasswordResetMailSender;
import com.devmozip.backend.service.PasswordResetService;
import com.devmozip.backend.service.impl.InMemoryPasswordResetService;
import com.jayway.jsonpath.JsonPath;

/**
 * 재설정 API를 실제 보안 설정·H2와 함께 확인한다.
 * 메일은 가짜이고 저장소는 메모리 구현이므로 실제 SMTP·Redis 검증이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final String OLD_PASSWORD = "Devmozip123";
    private static final String NEW_PASSWORD = "Changed456";
    private static final String INVALID_TOKEN_MESSAGE = "링크가 만료되었거나 이미 사용되었습니다. 비밀번호 재설정을 다시 요청해 주세요.";

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

    // 발송 작업을 요청 스레드에서 바로 실행해 메일 호출을 곧바로 확인한다
    @TestBean(name = "passwordResetMailExecutor", methodName = "syncExecutor")
    TaskExecutor mailExecutor;

    static TaskExecutor syncExecutor() {
        return new SyncTaskExecutor();
    }

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PasswordResetProperties properties;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean PasswordResetMailSender mailSender;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString();
        registry.add("jwt.secret", () -> secret + secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:password-reset;MODE=PostgreSQL");
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

    // 저장소가 테스트 사이에 유지되므로 재요청 제한에 걸리지 않게 테스트마다 다른 이메일을 쓴다
    private static String newEmail() {
        return UUID.randomUUID() + "@devmozip.test";
    }

    private String saveMember(String passwordHash, String kakaoId, MemberStatus status) {
        String email = newEmail();
        members.saveAndFlush(Member.builder()
                .email(email)
                .passwordHash(passwordHash)
                .kakaoId(kakaoId)
                .nickname(UUID.randomUUID().toString().substring(0, 20))
                .role(MemberRole.USER)
                .status(status)
                .build());
        return email;
    }

    private String savePasswordMember() {
        return saveMember(passwordEncoder.encode(OLD_PASSWORD), null, MemberStatus.ACTIVE);
    }

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String newPassword, String confirmPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                        .formatted(token, newPassword, confirmPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    /** 재설정을 요청하고 메일로 나간 토큰 원문을 돌려준다. */
    private String issueToken(String email) throws Exception {
        requestReset(email).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendResetLink(eq(email), token.capture());
        return token.getValue();
    }

    private void assertInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }

    @Test
    void 회원_상태와_관계없이_응답이_같다() throws Exception {
        List<String> emails = List.of(
                savePasswordMember(),
                newEmail(),
                saveMember(passwordEncoder.encode(OLD_PASSWORD), null, MemberStatus.SUSPENDED),
                saveMember(null, "kakao-" + UUID.randomUUID(), MemberStatus.ACTIVE));

        for (String email : emails) {
            requestReset(email).andExpect(status().isAccepted()).andExpect(content().string(""));
        }
    }

    @Test
    void 비밀번호_회원에게는_재설정_메일을_보낸다() throws Exception {
        String active = savePasswordMember();
        String warned = saveMember(passwordEncoder.encode(OLD_PASSWORD), null, MemberStatus.WARNED);

        requestReset(active);
        requestReset(warned);

        verify(mailSender).sendResetLink(eq(active), any());
        verify(mailSender).sendResetLink(eq(warned), any());
    }

    @Test
    void 카카오_전용_회원에게는_토큰_없는_안내만_보낸다() throws Exception {
        String email = saveMember(null, "kakao-" + UUID.randomUUID(), MemberStatus.ACTIVE);

        requestReset(email);

        verify(mailSender).sendKakaoGuide(email);
        verify(mailSender, never()).sendResetLink(any(), any());
    }

    @Test
    void 비밀번호와_카카오를_모두_가진_회원에게는_재설정_메일을_보낸다() throws Exception {
        String email = saveMember(passwordEncoder.encode(OLD_PASSWORD), "kakao-" + UUID.randomUUID(), MemberStatus.ACTIVE);

        requestReset(email);

        verify(mailSender).sendResetLink(eq(email), any());
        verify(mailSender, never()).sendKakaoGuide(any());
    }

    @Test
    void 미가입_정지_회원에게는_아무것도_보내지_않는다() throws Exception {
        requestReset(newEmail());
        requestReset(saveMember(passwordEncoder.encode(OLD_PASSWORD), null, MemberStatus.SUSPENDED));
        requestReset(saveMember(null, "kakao-" + UUID.randomUUID(), MemberStatus.SUSPENDED));

        verify(mailSender, never()).sendResetLink(any(), any());
        verify(mailSender, never()).sendKakaoGuide(any());
    }

    @Test
    void 재요청_간격_안에_다시_요청해도_같은_응답이고_메일은_한_통이다() throws Exception {
        String email = savePasswordMember();

        requestReset(email).andExpect(status().isAccepted());
        requestReset(email).andExpect(status().isAccepted());

        verify(mailSender, times(1)).sendResetLink(eq(email), any());
    }

    @Test
    void 이메일_형식이_틀리면_400이고_메일을_보내지_않는다() throws Exception {
        requestReset("not-an-email").andExpect(status().isBadRequest());

        verify(mailSender, never()).sendResetLink(any(), any());
    }

    @Test
    void 메일을_보낼_수_없는_환경이면_503이다() throws Exception {
        when(mailSender.isAvailable()).thenReturn(false);

        requestReset(savePasswordMember()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void 같은_IP의_요청이_한도를_넘으면_429이다() throws Exception {
        String ip = "203.0.113.77";
        for (int i = 0; i < properties.getIpHourlyLimit(); i++) {
            mvc.perform(post("/api/v1/auth/password/reset-request").header("X-Real-IP", ip)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + newEmail() + "\"}"))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(post("/api/v1/auth/password/reset-request").header("X-Real-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + newEmail() + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 재설정하면_새_비밀번호로_로그인되고_이전_비밀번호는_401이다() throws Exception {
        String email = savePasswordMember();

        reset(issueToken(email), NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 재설정하면_기존_리프레시_토큰으로_재발급할_수_없다() throws Exception {
        String email = savePasswordMember();
        String loginBody = login(email, OLD_PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        reset(issueToken(email), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_토큰은_다시_쓸_수_없고_비밀번호가_유지된다() throws Exception {
        String email = savePasswordMember();
        String token = issueToken(email);
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertInvalidToken(reset(token, "Another789", "Another789"));

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 위조한_토큰은_401이다() throws Exception {
        assertInvalidToken(reset("forged-token", NEW_PASSWORD, NEW_PASSWORD));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        assertInvalidToken(mvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(NEW_PASSWORD, NEW_PASSWORD))));
    }

    @Test
    void 수명이_지난_토큰은_401이고_비밀번호가_유지된다() throws Exception {
        String email = savePasswordMember();
        Duration tokenTtl = properties.getTokenTtl();
        properties.setTokenTtl(Duration.ZERO);
        try {
            assertInvalidToken(reset(issueToken(email), NEW_PASSWORD, NEW_PASSWORD));
        } finally {
            properties.setTokenTtl(tokenTtl);
        }

        login(email, OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 새로_요청하면_이전_토큰은_쓸_수_없다() throws Exception {
        String email = savePasswordMember();
        Duration cooldown = properties.getEmailCooldown();
        properties.setEmailCooldown(Duration.ZERO);
        try {
            requestReset(email);
            requestReset(email);
        } finally {
            properties.setEmailCooldown(cooldown);
        }
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(mailSender, times(2)).sendResetLink(eq(email), tokens.capture());

        assertInvalidToken(reset(tokens.getAllValues().get(0), NEW_PASSWORD, NEW_PASSWORD));
        reset(tokens.getAllValues().get(1), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @CsvSource({
            "short1, short1",
            "onlyletters, onlyletters",
            "Changed456, Different789"
    })
    void 입력_규칙을_위반하면_400이고_같은_토큰으로_다시_시도할_수_있다(String newPassword, String confirmPassword) throws Exception {
        String email = savePasswordMember();
        String token = issueToken(email);

        reset(token, newPassword, confirmPassword).andExpect(status().isBadRequest());
        login(email, OLD_PASSWORD).andExpect(status().isOk());

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void BCrypt_길이를_넘으면_400이고_같은_토큰으로_다시_시도할_수_있다() throws Exception {
        String email = savePasswordMember();
        String token = issueToken(email);
        String tooLong = "가".repeat(25) + "a1";

        reset(token, tooLong, tooLong).andExpect(status().isBadRequest());

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void 요청_뒤_정지된_회원은_같은_문구의_401이고_비밀번호가_유지된다() throws Exception {
        String email = savePasswordMember();
        String token = issueToken(email);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE email = ?", email);

        assertInvalidToken(reset(token, NEW_PASSWORD, NEW_PASSWORD));

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hash)).isTrue();
    }
}
