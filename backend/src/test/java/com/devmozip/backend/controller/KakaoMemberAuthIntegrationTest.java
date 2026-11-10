package com.devmozip.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.devmozip.backend.client.KakaoClient;
import com.devmozip.backend.config.KakaoProperties;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InMemoryRefreshTokenStore;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AuthService;
import com.jayway.jsonpath.JsonPath;

/**
 * 비밀번호 없는 카카오 회원과 일반 회원의 인증을 실제 보안 설정·H2와 함께 확인한다.
 * KakaoClient를 가짜로 바꿔 카카오 서버는 호출하지 않으므로 실제 카카오 로그인 검증이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM users")
class KakaoMemberAuthIntegrationTest {

    private static final String PASSWORD = "Devmozip123";
    private static final String KAKAO_ID = "123456789";

    @TestConfiguration
    static class RefreshTokenStoreConfiguration {
        @Bean
        @Primary
        RefreshTokenStore inMemoryRefreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionTemplate transaction;
    @Autowired KakaoProperties kakaoProperties;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean KakaoClient kakaoClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString();
        registry.add("jwt.secret", () -> secret + secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:kakao-member-auth;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
        // 개발자 PC의 환경변수와 관계없이 카카오 값이 없는 환경을 재현한다
        registry.add("kakao.rest-api-key", () -> "");
        registry.add("kakao.client-secret", () -> "");
        registry.add("kakao.domain-name", () -> "");
        registry.add("kakao.redirect-path", () -> "");
    }

    private Member saveMember(String email, String passwordHash, String kakaoId, MemberStatus status) {
        return members.saveAndFlush(Member.builder()
                .email(email)
                .passwordHash(passwordHash)
                .kakaoId(kakaoId)
                .nickname(UUID.randomUUID().toString().substring(0, 20))
                .role(MemberRole.USER)
                .status(status)
                .build());
    }

    private String refresh(String refreshToken) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private void kakaoAnswers() {
        when(kakaoClient.requestAccessToken("code")).thenReturn("kakao-access");
        when(kakaoClient.fetchKakaoId("kakao-access")).thenReturn(KAKAO_ID);
    }

    private static String signupBody(String email, String nickname, String extraFields) {
        return """
                {"email":"%s","nickname":"%s","job":"백엔드 개발자","career":"3년 차",
                 "skills":["Java"],"interests":["AI"],"available":true%s}""".formatted(email, nickname, extraFields);
    }

    private static String kakaoToken(String token) {
        return ",\"kakaoSignupToken\":\"" + token + "\"";
    }

    private ResultActions signup(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions callback(String intent) throws Exception {
        return mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code").param("intent", intent));
    }

    @Test
    void 카카오_가입부터_재로그인까지_이어진다() throws Exception {
        kakaoAnswers();

        // 1. 처음 온 카카오 계정: 토큰 없이 가입 토큰만 받는다
        String callbackBody = callback("login")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String signupToken = JsonPath.read(callbackBody, "$.kakaoSignupToken");

        // 2. 같은 회원가입 주소에 비밀번호 없이 가입한다. 카카오 회원번호는 응답에 내려가지 않는다
        String signupBody = signup(signupBody("New@Devmozip.Test", "카카오회원", kakaoToken(signupToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@devmozip.test"))
                .andExpect(jsonPath("$.nickname").value("카카오회원"))
                .andExpect(jsonPath("$.kakaoId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Member saved = members.findByKakaoId(KAKAO_ID).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("new@devmozip.test");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getSkills()).containsExactly("Java");

        // 3. 가입 응답의 토큰으로 API를 쓰고 재발급받는다
        String accessToken = JsonPath.read(signupBody, "$.accessToken");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new@devmozip.test"));
        refresh(JsonPath.read(signupBody, "$.refreshToken"));

        // 4. 다시 오면 같은 회원으로 로그인한다
        callback("login")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.kakaoSignupToken").doesNotExist());

        // 5. 가입 화면에서 시작하면 로그인시키지 않는다
        callback("signup")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void 이메일_가입은_기존처럼_비밀번호를_저장하고_토큰을_주지_않는다() throws Exception {
        String password = ",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"";

        signup(signupBody("email@devmozip.test", "이메일회원", password))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        Member saved = members.findByEmail("email@devmozip.test").orElseThrow();
        assertThat(saved.getKakaoId()).isNull();
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void 가입_토큰_없이_비밀번호도_없으면_가입할_수_없다() throws Exception {
        signup(signupBody("new@devmozip.test", "카카오회원", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("영문과 숫자를 포함해 8~64자로 입력해 주세요."));
        signup(signupBody("new@devmozip.test", "카카오회원", kakaoToken(" ")))
                .andExpect(status().isBadRequest());

        assertThat(members.count()).isZero();
    }

    @Test
    void 가입_토큰과_비밀번호를_함께_보내면_400() throws Exception {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);
        String extra = ",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"" + kakaoToken(token);

        signup(signupBody("new@devmozip.test", "카카오회원", extra))
                .andExpect(status().isBadRequest());

        assertThat(members.count()).isZero();
    }

    @Test
    void 위조하거나_다른_종류의_토큰으로는_카카오_가입을_할_수_없다() throws Exception {
        signup(signupBody("new@devmozip.test", "카카오회원", kakaoToken("forged-token")))
                .andExpect(status().isUnauthorized());
        signup(signupBody("new@devmozip.test", "카카오회원", kakaoToken(jwtProvider.createAccessToken(KAKAO_ID, "USER"))))
                .andExpect(status().isUnauthorized());

        assertThat(members.count()).isZero();
    }

    @Test
    void 요청_본문의_카카오_회원번호는_무시한다() throws Exception {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);
        String extra = ",\"kakaoId\":\"987654321\",\"kakao_id\":\"987654321\"" + kakaoToken(token);

        signup(signupBody("new@devmozip.test", "카카오회원", extra)).andExpect(status().isCreated());

        assertThat(members.findByKakaoId(KAKAO_ID)).isPresent();
        assertThat(members.findByKakaoId("987654321")).isEmpty();
    }

    @Test
    void 같은_가입_토큰으로_두_번_가입할_수_없다() throws Exception {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);

        signup(signupBody("first@devmozip.test", "첫번째회원", kakaoToken(token))).andExpect(status().isCreated());
        signup(signupBody("second@devmozip.test", "두번째회원", kakaoToken(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 회원입니다."));

        assertThat(members.count()).isEqualTo(1);
    }

    @Test
    void 기존_이메일로_카카오_가입하면_자동_연결하지_않고_안내한다() throws Exception {
        saveMember("email@devmozip.test", passwordEncoder.encode(PASSWORD), null, MemberStatus.ACTIVE);
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);

        signup(signupBody("Email@Devmozip.Test", "카카오회원", kakaoToken(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("이미 가입된 이메일입니다. 기존 계정으로 로그인한 뒤 개인정보 설정에서 카카오를 연동해 주세요."));

        assertThat(members.findByEmail("email@devmozip.test").orElseThrow().getKakaoId()).isNull();
    }

    @Test
    void 정지된_회원의_카카오_로그인은_403() throws Exception {
        kakaoAnswers();
        saveMember("kakao@devmozip.test", null, KAKAO_ID, MemberStatus.SUSPENDED);

        callback("login").andExpect(status().isForbidden());
    }

    @Test
    void 알_수_없는_의도면_카카오를_호출하지_않고_400() throws Exception {
        callback("link").andExpect(status().isBadRequest());

        verifyNoInteractions(kakaoClient);
    }

    @Test
    void 카카오_값이_없어도_서버가_뜨고_카카오_기능만_꺼진다() {
        assertThat(kakaoProperties.isConfigured()).isFalse();
    }

    @Test
    void 비밀번호_없는_카카오_회원도_재발급받아_API를_쓴다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID, MemberStatus.ACTIVE);

        LoginResponse tokens = authService.issueTokens(member);
        String accessToken = refresh(tokens.refreshToken());

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kakao@devmozip.test"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void 비밀번호_있는_회원의_로그인과_재발급은_그대로다() throws Exception {
        saveMember("email@devmozip.test", passwordEncoder.encode(PASSWORD), null, MemberStatus.ACTIVE);

        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"email@devmozip.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = refresh(JsonPath.read(body, "$.refreshToken"));

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("email@devmozip.test"));
    }

    @Test
    void 비밀번호_없는_회원은_이메일로_로그인할_수_없다() throws Exception {
        saveMember("kakao@devmozip.test", null, KAKAO_ID, MemberStatus.ACTIVE);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kakao@devmozip.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 가입_토큰으로는_API_인증도_재발급도_할_수_없다() throws Exception {
        saveMember("kakao@devmozip.test", null, KAKAO_ID, MemberStatus.ACTIVE);
        String signupToken = jwtProvider.createKakaoSignupToken(KAKAO_ID);

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + signupToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + signupToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 정지된_카카오_회원에게는_토큰을_발급하지_않는다() {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID, MemberStatus.SUSPENDED);

        assertThatThrownBy(() -> authService.issueTokens(member)).isInstanceOf(LockedException.class);
    }

    @Test
    void 같은_카카오_회원번호를_두_회원이_가질_수_없다() {
        saveMember("first@devmozip.test", null, KAKAO_ID, MemberStatus.ACTIVE);

        assertThatThrownBy(() -> saveMember("second@devmozip.test", null, KAKAO_ID, MemberStatus.ACTIVE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 카카오를_연동하지_않은_회원은_여러_명일_수_있다() {
        saveMember("first@devmozip.test", passwordEncoder.encode(PASSWORD), null, MemberStatus.ACTIVE);
        saveMember("second@devmozip.test", passwordEncoder.encode(PASSWORD), null, MemberStatus.ACTIVE);

        assertThat(members.count()).isEqualTo(2);
    }

    @Test
    void 잠금_조회로_찾아_연동과_비밀번호를_바꾸면_저장된다() {
        saveMember("email@devmozip.test", null, null, MemberStatus.ACTIVE);

        transaction.executeWithoutResult(status -> {
            Member member = members.findForAccountUpdateByEmail("email@devmozip.test").orElseThrow();
            member.linkKakao(KAKAO_ID);
            member.changePasswordHash("new-hash");
        });

        Member linked = members.findByKakaoId(KAKAO_ID).orElseThrow();
        assertThat(linked.getEmail()).isEqualTo("email@devmozip.test");
        assertThat(linked.getPasswordHash()).isEqualTo("new-hash");

        transaction.executeWithoutResult(status ->
                members.findForAccountUpdateByEmail("email@devmozip.test").orElseThrow().unlinkKakao());

        assertThat(members.findByKakaoId(KAKAO_ID)).isEmpty();
    }
}
