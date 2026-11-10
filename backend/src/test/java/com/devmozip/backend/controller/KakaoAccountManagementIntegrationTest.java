package com.devmozip.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.devmozip.backend.client.KakaoClient;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InMemoryRefreshTokenStore;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AuthService;

/**
 * 카카오 연동 관리와 비밀번호 설정을 실제 보안 설정·H2와 함께 확인한다.
 * KakaoClient를 가짜로 바꿔 카카오 서버는 호출하지 않으므로 실제 카카오 연동 검증이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM users")
class KakaoAccountManagementIntegrationTest {

    private static final String ACCOUNTS_URL = "/api/v1/users/me/social-accounts";
    private static final String KAKAO_URL = ACCOUNTS_URL + "/kakao";
    private static final String PASSWORD_URL = "/api/v1/users/me/password";
    private static final String PASSWORD = "Devmozip123";
    private static final String OTHER_PASSWORD = "Another456";
    private static final String KAKAO_ID = "123456789";
    private static final String OTHER_KAKAO_ID = "987654321";

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
    @MockitoBean KakaoClient kakaoClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String secret = UUID.randomUUID().toString();
        registry.add("jwt.secret", () -> secret + secret);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:kakao-account-management;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
    }

    private Member saveMember(String email, String password, String kakaoId) {
        return members.saveAndFlush(Member.builder()
                .email(email)
                .passwordHash(password != null ? passwordEncoder.encode(password) : null)
                .kakaoId(kakaoId)
                .nickname(UUID.randomUUID().toString().substring(0, 20))
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build());
    }

    private Member member(String email) {
        return members.findByEmail(email).orElseThrow();
    }

    private String bearer(Member member) {
        return "Bearer " + authService.issueTokens(member).accessToken();
    }

    private void kakaoAnswers(String kakaoId) {
        when(kakaoClient.requestAccessToken("code")).thenReturn("kakao-access");
        when(kakaoClient.fetchKakaoId("kakao-access")).thenReturn(kakaoId);
    }

    private ResultActions link(Member member) throws Exception {
        return mvc.perform(post(KAKAO_URL).header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"code\"}"));
    }

    private ResultActions unlink(Member member) throws Exception {
        return mvc.perform(delete(KAKAO_URL).param("code", "code").header("Authorization", bearer(member)));
    }

    private ResultActions setPassword(Member member, String password) throws Exception {
        return mvc.perform(patch(PASSWORD_URL).header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"" + password + "\",\"confirmPassword\":\"" + password + "\"}"));
    }

    private ResultActions emailLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void 토큰이_없으면_모두_401() throws Exception {
        mvc.perform(get(ACCOUNTS_URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(KAKAO_URL).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"code\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(KAKAO_URL).param("code", "code")).andExpect(status().isUnauthorized());
        mvc.perform(patch(PASSWORD_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(kakaoClient);
    }

    @Test
    void 비밀번호_있는_회원이_연동하면_카카오로_같은_회원에_로그인한다() throws Exception {
        Member member = saveMember("email@devmozip.test", PASSWORD, null);
        kakaoAnswers(KAKAO_ID);

        mvc.perform(get(ACCOUNTS_URL).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoLinked").value(false))
                .andExpect(jsonPath("$.passwordSet").value(true));

        link(member).andExpect(status().isNoContent());

        assertThat(member("email@devmozip.test").getKakaoId()).isEqualTo(KAKAO_ID);
        mvc.perform(get(ACCOUNTS_URL).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoLinked").value(true))
                .andExpect(jsonPath("$.kakaoId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN"));
        emailLogin("email@devmozip.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 비밀번호_없는_회원도_연동_상태를_조회한다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID);

        mvc.perform(get(ACCOUNTS_URL).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoLinked").value(true))
                .andExpect(jsonPath("$.passwordSet").value(false));
    }

    @Test
    void 다른_회원이_쓰는_카카오_계정은_연동할_수_없다() throws Exception {
        saveMember("owner@devmozip.test", PASSWORD, KAKAO_ID);
        Member member = saveMember("email@devmozip.test", PASSWORD, null);
        kakaoAnswers(KAKAO_ID);

        link(member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 다른 회원에 연동된 카카오 계정입니다."));

        assertThat(member("email@devmozip.test").getKakaoId()).isNull();
        assertThat(member("owner@devmozip.test").getKakaoId()).isEqualTo(KAKAO_ID);
    }

    @Test
    void 이미_연동한_회원은_인가_코드를_쓰기_전에_거절한다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", PASSWORD, KAKAO_ID);

        link(member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 카카오 계정이 연동되어 있습니다."));

        verifyNoInteractions(kakaoClient);
    }

    @Test
    void 비밀번호_없는_카카오_회원이_비밀번호를_설정하면_이메일로_로그인한다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID);
        emailLogin("kakao@devmozip.test", PASSWORD).andExpect(status().isUnauthorized());

        setPassword(member, PASSWORD).andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches(PASSWORD, member("kakao@devmozip.test").getPasswordHash())).isTrue();
        emailLogin("kakao@devmozip.test", PASSWORD).andExpect(status().isOk());
        mvc.perform(get(ACCOUNTS_URL).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.passwordSet").value(true));
    }

    @Test
    void 비밀번호를_한_번_설정한_뒤에는_현재_비밀번호_없이_바꿀_수_없다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID);
        setPassword(member, PASSWORD).andExpect(status().isNoContent());

        setPassword(member, OTHER_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현재 비밀번호를 입력해 주세요."));

        emailLogin("kakao@devmozip.test", PASSWORD).andExpect(status().isOk());
        emailLogin("kakao@devmozip.test", OTHER_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호가_있는_일반_회원은_현재_비밀번호_없이_바꿀_수_없다() throws Exception {
        Member member = saveMember("email@devmozip.test", PASSWORD, null);

        setPassword(member, OTHER_PASSWORD).andExpect(status().isBadRequest());

        emailLogin("email@devmozip.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 카카오를_연동하지_않은_회원은_비밀번호를_설정할_수_없다() throws Exception {
        Member member = saveMember("nopassword@devmozip.test", null, null);

        setPassword(member, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("비밀번호를 설정할 수 없는 계정입니다."));

        assertThat(member("nopassword@devmozip.test").getPasswordHash()).isNull();
    }

    @Test
    void 요청_본문에_다른_회원을_적어도_토큰의_주인만_바뀐다() throws Exception {
        Member mine = saveMember("mine@devmozip.test", null, KAKAO_ID);
        saveMember("other@devmozip.test", null, OTHER_KAKAO_ID);
        Member linker = saveMember("linker@devmozip.test", PASSWORD, null);
        kakaoAnswers("555555555");

        mvc.perform(patch(PASSWORD_URL).header("Authorization", bearer(mine))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@devmozip.test\",\"newPassword\":\"" + PASSWORD
                                + "\",\"confirmPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post(KAKAO_URL).header("Authorization", bearer(linker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@devmozip.test\",\"kakaoId\":\"111\",\"code\":\"code\"}"))
                .andExpect(status().isNoContent());

        assertThat(member("mine@devmozip.test").getPasswordHash()).isNotNull();
        assertThat(member("other@devmozip.test").getPasswordHash()).isNull();
        assertThat(member("other@devmozip.test").getKakaoId()).isEqualTo(OTHER_KAKAO_ID);
        assertThat(member("linker@devmozip.test").getKakaoId()).isEqualTo("555555555");
    }

    @Test
    void 비밀번호_없는_회원은_카카오를_호출하지_않고_해제를_거절한다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID);

        unlink(member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("비밀번호를 먼저 설정해야 카카오 연동을 해제할 수 있습니다."));

        verifyNoInteractions(kakaoClient);
        assertThat(member("kakao@devmozip.test").getKakaoId()).isEqualTo(KAKAO_ID);
    }

    @Test
    void 비밀번호를_설정한_뒤에는_연동을_해제하고_이메일로_로그인한다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", null, KAKAO_ID);
        setPassword(member, PASSWORD).andExpect(status().isNoContent());
        kakaoAnswers(KAKAO_ID);

        unlink(member).andExpect(status().isNoContent());

        verify(kakaoClient).unlink("kakao-access");
        assertThat(member("kakao@devmozip.test").getKakaoId()).isNull();
        mvc.perform(get(ACCOUNTS_URL).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.kakaoLinked").value(false))
                .andExpect(jsonPath("$.passwordSet").value(true));
        emailLogin("kakao@devmozip.test", PASSWORD).andExpect(status().isOk());

        // 해제한 뒤 같은 카카오 계정은 더 이상 이 회원으로 로그인되지 않는다
        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
    }

    @Test
    void 연동한_계정과_다른_카카오_계정으로는_해제할_수_없다() throws Exception {
        Member member = saveMember("kakao@devmozip.test", PASSWORD, KAKAO_ID);
        kakaoAnswers(OTHER_KAKAO_ID);

        unlink(member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("연동한 카카오 계정으로 인증해 주세요."));

        verify(kakaoClient, never()).unlink(any());
        assertThat(member("kakao@devmozip.test").getKakaoId()).isEqualTo(KAKAO_ID);
    }

    @Test
    void 연동하지_않은_회원의_해제는_카카오를_호출하지_않고_거절한다() throws Exception {
        Member member = saveMember("email@devmozip.test", PASSWORD, null);

        unlink(member).andExpect(status().isConflict());

        verifyNoInteractions(kakaoClient);
    }
}
