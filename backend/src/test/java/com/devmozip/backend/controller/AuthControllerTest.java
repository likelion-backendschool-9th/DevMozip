package com.devmozip.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.config.SecurityConfig;
import com.devmozip.backend.dto.auth.LoginRequest;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.dto.auth.SignupOptionsResponse;
import com.devmozip.backend.dto.auth.SignupResponse;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.AuthService;
import com.devmozip.backend.service.MemberService;

/** 요청·응답 DTO(record)가 JSON과 그대로 오가는지 확인한다. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AuthControllerTest {

    private static final String SIGNUP_BODY = """
            {"email":"new@devmozip.demo","password":"Devmozip123","confirmPassword":"Devmozip123",
             "nickname":"새회원","job":"백엔드 개발자","career":"3년 차",
             "stacks":["Java"],"interests":["AI"],"available":true}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private MemberService memberService;
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

        mvc.perform(post("/api/v1/auth/login")
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
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout("refresh");
    }

    @Test
    void 토큰_없이_회원가입_선택지를_돌려준다() throws Exception {
        when(memberService.getSignupOptions()).thenReturn(
                new SignupOptionsResponse(List.of("백엔드 개발자"), List.of("Java"), List.of("AI")));

        mvc.perform(get("/api/v1/auth/signup/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobCategories[0]").value("백엔드 개발자"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.interests[0]").value("AI"));
    }

    @Test
    void 토큰_없이_이메일_중복을_확인한다() throws Exception {
        when(memberService.isEmailAvailable("new@devmozip.demo")).thenReturn(true);

        mvc.perform(get("/api/v1/auth/check-email").param("email", "new@devmozip.demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 토큰_없이_닉네임_중복을_확인한다() throws Exception {
        when(memberService.isNicknameAvailable("김하늘")).thenReturn(false);

        mvc.perform(get("/api/v1/auth/check-nickname").param("nickname", "김하늘"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 중복_확인에_값이_없으면_400() throws Exception {
        mvc.perform(get("/api/v1/auth/check-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 토큰_없이_회원가입하면_201과_회원_정보를_돌려준다() throws Exception {
        when(memberService.signup(any())).thenReturn(new SignupResponse(1L, "new@devmozip.demo", "새회원"));

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("new@devmozip.demo"))
                .andExpect(jsonPath("$.nickname").value("새회원"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void 비밀번호_규칙에_맞지_않으면_400과_안내_문구를_돌려준다() throws Exception {
        String body = SIGNUP_BODY.replace("\"password\":\"Devmozip123\"", "\"password\":\"short\"");

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("영문과 숫자를 포함해 8~64자로 입력해 주세요."));
    }

    @Test
    void 기술_스택을_선택하지_않으면_400() throws Exception {
        String body = SIGNUP_BODY.replace("\"stacks\":[\"Java\"]", "\"stacks\":[]");

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기술 스택을 하나 이상 선택해 주세요."));
    }

    @Test
    void 이미_가입된_이메일이면_409() throws Exception {
        when(memberService.signup(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."));

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
    }
}
