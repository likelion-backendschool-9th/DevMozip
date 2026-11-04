package com.devmozip.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devmozip.backend.config.SecurityConfig;
import com.devmozip.backend.dto.auth.LoginRequest;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.AuthService;

/** 요청·응답 DTO(record)가 JSON과 그대로 오가는지 확인한다. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private SuspendedMemberStore suspendedMemberStore;
    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void 로그인_요청을_읽고_토큰을_돌려준다() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.builder()
                .accessToken("access").refreshToken("refresh").tokenType("Bearer").build());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member@devmozip.demo\",\"password\":\"Devmozip123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).login(new LoginRequest("member@devmozip.demo", "Devmozip123!"));
    }

    @Test
    void 로그아웃_요청의_리프레시_토큰을_읽는다() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout("refresh");
    }
}
