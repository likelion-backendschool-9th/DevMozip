package com.devmozip.backend.controller;

import com.devmozip.backend.config.SecurityConfig;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.service.ProfileService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, ProfileExceptionHandler.class,
        JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class ProfileControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfileService service;

    @MockitoBean
    JwtProvider jwt;
    @MockitoBean
    com.devmozip.backend.security.jwt.SuspendedMemberStore suspendedMemberStore;
    @MockitoBean
    com.devmozip.backend.repository.MemberRepository memberRepository;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        String password = UUID.randomUUID().toString();
        registry.add("auth.bootstrap.user-password", () -> password);
        registry.add("auth.bootstrap.admin-password", () -> password);
    }

    @Test
    void anonymousUserCanReadPublicProfileWithoutPrivateFields() throws Exception {
        when(service.findBySlug("dev-tag")).thenReturn(new ProfileDetailResponse(
                1L, "dev-tag", "개발자",
                java.util.List.of("백엔드 개발자"),
                "함께 개발해요", "2년 차", null, null, "AVAILABLE", new BigDecimal("36.50"), "평일 저녁"));

        mvc.perform(get("/api/v1/users/dev-tag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tag").value("dev-tag"))
                .andExpect(jsonPath("$.jobCategories[0]").value("백엔드 개발자"))
                .andExpect(jsonPath("$.summary").value("함께 개발해요"))
                .andExpect(jsonPath("$.career").value("2년 차"))
                .andExpect(jsonPath("$.contactHours").value("평일 저녁"))
                .andExpect(jsonPath("$.trustScore").value(36.5))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.kakaoId").doesNotExist())
                .andExpect(jsonPath("$.paymentMethods").doesNotExist());
    }

    @Test
    void missingProfileReturnsProblemDetail() throws Exception {
        when(service.findBySlug("missing")).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."));
        mvc.perform(get("/api/v1/users/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("프로필을 찾을 수 없습니다."));
    }

    @Test
    void unrelatedRoutesStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousWritesStillRequireAuthentication() throws Exception {
        mvc.perform(put("/api/v1/users/dev-tag")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
