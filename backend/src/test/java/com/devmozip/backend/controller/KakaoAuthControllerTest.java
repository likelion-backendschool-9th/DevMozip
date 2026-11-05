package com.devmozip.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.config.SecurityConfig;
import com.devmozip.backend.dto.auth.KakaoAuthorizeResponse;
import com.devmozip.backend.dto.auth.KakaoCallbackResponse;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.KakaoAuthService;

/** 토큰 없이 호출할 수 있는지와 상태별 응답 형식, 오류 상태 코드를 확인한다. */
@WebMvcTest(KakaoAuthController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class KakaoAuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KakaoAuthService kakaoAuthService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private SuspendedMemberStore suspendedMemberStore;
    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 토큰_없이_인가_주소를_돌려준다() throws Exception {
        when(kakaoAuthService.authorize("state")).thenReturn(new KakaoAuthorizeResponse("https://kauth.kakao.test"));

        mvc.perform(get("/api/v1/auth/kakao").param("state", "state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value("https://kauth.kakao.test"));
    }

    @Test
    void state가_없으면_400() throws Exception {
        mvc.perform(get("/api/v1/auth/kakao")).andExpect(status().isBadRequest());

        verifyNoInteractions(kakaoAuthService);
    }

    @Test
    void 로그인_응답에는_토큰만_담는다() throws Exception {
        when(kakaoAuthService.handleCallback("code", "login")).thenReturn(KakaoCallbackResponse.login(
                LoginResponse.builder().accessToken("access").refreshToken("refresh").tokenType("Bearer").build()));

        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN"))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.kakaoSignupToken").doesNotExist());
    }

    @Test
    void 가입이_필요하면_가입_토큰만_담는다() throws Exception {
        when(kakaoAuthService.handleCallback("code", "signup"))
                .thenReturn(KakaoCallbackResponse.signupRequired("signup-token"));

        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code").param("intent", "signup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.kakaoSignupToken").value("signup-token"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        verify(kakaoAuthService).handleCallback("code", "signup");
    }

    @Test
    void 이미_등록된_회원이면_상태만_돌려준다() throws Exception {
        when(kakaoAuthService.handleCallback("code", "signup")).thenReturn(KakaoCallbackResponse.alreadyRegistered());

        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code").param("intent", "signup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.kakaoSignupToken").doesNotExist());
    }

    @Test
    void 인가_코드가_없으면_400() throws Exception {
        mvc.perform(get("/api/v1/auth/kakao/callback")).andExpect(status().isBadRequest());

        verifyNoInteractions(kakaoAuthService);
    }

    @Test
    void 정지된_회원은_403() throws Exception {
        when(kakaoAuthService.handleCallback("code", "login")).thenThrow(new LockedException("정지"));

        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 카카오_오류_상태와_안내_문구를_그대로_돌려준다() throws Exception {
        when(kakaoAuthService.handleCallback("code", "login"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 서버에 연결할 수 없습니다."));

        mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("카카오 서버에 연결할 수 없습니다."));
    }

    @Test
    void 카카오_설정이_없으면_503() throws Exception {
        when(kakaoAuthService.authorize("state"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인을 사용할 수 없습니다."));

        mvc.perform(get("/api/v1/auth/kakao").param("state", "state"))
                .andExpect(status().isServiceUnavailable());
    }
}
