package com.devmozip.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devmozip.backend.config.SecurityConfig;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;

/**
 * GET /api/v1/users/me 를 실제 보안 설정(SecurityConfig + JwtAuthenticationFilter)과 함께 확인한다.
 * DB 없이 돌도록 토큰 해석과 저장소만 가짜로 바꾼다.
 */
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class MemberControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private SuspendedMemberStore suspendedMemberStore;
    @MockitoBean
    private MemberRepository memberRepository;

    @BeforeEach
    void validToken() {
        when(jwtProvider.validateAccessToken(TOKEN)).thenReturn(true);
        when(jwtProvider.getUsername(TOKEN)).thenReturn("premium@devmozip.demo");
        when(jwtProvider.getRole(TOKEN)).thenReturn("PREMIUM");
    }

    @Test
    void 토큰의_주인과_역할을_접두어_없이_돌려준다() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("premium@devmozip.demo"))
                .andExpect(jsonPath("$.role").value("PREMIUM"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 정지된_회원은_토큰이_유효해도_403() throws Exception {
        when(suspendedMemberStore.isSuspended("premium@devmozip.demo")).thenReturn(true);

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }
}
