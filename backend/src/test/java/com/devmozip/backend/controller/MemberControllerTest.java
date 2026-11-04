package com.devmozip.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devmozip.backend.config.SecurityConfig;
import com.devmozip.backend.dto.member.AvailabilityRequest;
import com.devmozip.backend.dto.member.AvailabilityResponse;
import com.devmozip.backend.entity.Member.CollaborationStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.MemberService;

/**
 * GET /api/v1/users/me, PATCH /api/v1/users/me/availability 를 실제 보안 설정(SecurityConfig + JwtAuthenticationFilter)과 함께 확인한다.
 * DB 없이 돌도록 토큰 해석과 저장소만 가짜로 바꾼다.
 */
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class MemberControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MemberService memberService;
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

    @Test
    void 토큰의_주인_기준으로_협업_상태와_연락_시간을_바꾼다() throws Exception {
        when(memberService.updateAvailability("premium@devmozip.demo",
                new AvailabilityRequest(CollaborationStatus.BUSY, "평일 저녁")))
                .thenReturn(new AvailabilityResponse(CollaborationStatus.BUSY, "평일 저녁"));

        mvc.perform(patch("/api/v1/users/me/availability").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collaborationStatus\":\"BUSY\",\"contactHours\":\"평일 저녁\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collaborationStatus").value("BUSY"))
                .andExpect(jsonPath("$.contactHours").value("평일 저녁"));
    }

    @Test
    void 토큰_없이_협업_상태를_바꾸면_401() throws Exception {
        mvc.perform(patch("/api/v1/users/me/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collaborationStatus\":\"BUSY\"}"))
                .andExpect(status().isUnauthorized());

        verify(memberService, never()).updateAvailability(any(), any());
    }

    @Test
    void 없는_협업_상태면_400() throws Exception {
        mvc.perform(patch("/api/v1/users/me/availability").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collaborationStatus\":\"SLEEPING\"}"))
                .andExpect(status().isBadRequest());

        verify(memberService, never()).updateAvailability(any(), any());
    }

    @Test
    void 연락_시간이_100자를_넘으면_400() throws Exception {
        mvc.perform(patch("/api/v1/users/me/availability").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactHours\":\"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("연락 가능 시간은 100자 이내로 입력해 주세요."));
    }
}
