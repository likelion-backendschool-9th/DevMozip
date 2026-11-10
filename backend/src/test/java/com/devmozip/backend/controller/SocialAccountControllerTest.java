package com.devmozip.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.devmozip.backend.dto.member.SocialAccountsResponse;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.KakaoAuthService;
import com.devmozip.backend.service.MemberService;

/** 인증 주체 전달과 경로·요청 검증을 실제 보안 설정과 함께 확인한다. */
@WebMvcTest(SocialAccountController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class SocialAccountControllerTest {

    private static final String TOKEN = "access-token";
    private static final String EMAIL = "member@devmozip.test";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private KakaoAuthService kakaoAuthService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private SuspendedMemberStore suspendedMemberStore;
    @MockitoBean
    private MemberRepository memberRepository;

    @BeforeEach
    void validToken() {
        when(jwtProvider.validateAccessToken(TOKEN)).thenReturn(true);
        when(jwtProvider.getUsername(TOKEN)).thenReturn(EMAIL);
        when(jwtProvider.getRole(TOKEN)).thenReturn("USER");
    }

    @Test
    void 토큰의_주인_기준으로_연동_상태를_돌려준다() throws Exception {
        when(memberService.getSocialAccounts(EMAIL)).thenReturn(new SocialAccountsResponse(true, false));

        mvc.perform(get("/api/v1/users/me/social-accounts").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoLinked").value(true))
                .andExpect(jsonPath("$.passwordSet").value(false));
    }

    @Test
    void 토큰의_주인에_인가_코드로_연동한다() throws Exception {
        mvc.perform(post("/api/v1/users/me/social-accounts/kakao").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"code\"}"))
                .andExpect(status().isNoContent());

        verify(kakaoAuthService).link(EMAIL, "code");
    }

    @Test
    void 토큰의_주인의_연동을_해제한다() throws Exception {
        mvc.perform(delete("/api/v1/users/me/social-accounts/kakao").param("code", "code")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());

        verify(kakaoAuthService).unlink(EMAIL, "code");
    }

    @Test
    void 지원하지_않는_제공자나_카카오_회원번호를_경로로_보내면_404() throws Exception {
        mvc.perform(post("/api/v1/users/me/social-accounts/123456789").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"code\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/users/me/social-accounts/google").param("code", "code")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());

        verifyNoInteractions(kakaoAuthService);
    }

    @Test
    void 인가_코드가_없으면_400() throws Exception {
        mvc.perform(post("/api/v1/users/me/social-accounts/kakao").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("카카오 인가 코드가 필요합니다."));
        mvc.perform(delete("/api/v1/users/me/social-accounts/kakao").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());

        verify(kakaoAuthService, never()).link(any(), any());
        verify(kakaoAuthService, never()).unlink(any(), any());
    }

    @Test
    void 정지된_회원은_연동을_바꿀_수_없다() throws Exception {
        when(suspendedMemberStore.isSuspended(EMAIL)).thenReturn(true);

        mvc.perform(post("/api/v1/users/me/social-accounts/kakao").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"code\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(kakaoAuthService);
    }
}
