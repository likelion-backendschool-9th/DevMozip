package com.devmozip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;

/** 비밀번호 없는 회원의 토큰 발급·재발급 규칙을 DB 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "kakao@devmozip.test";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private AuthService authService;

    private static Member kakaoMember(MemberRole role, MemberStatus status) {
        return Member.builder().email(EMAIL).kakaoId("123456789").role(role).status(status).build();
    }

    private void validRefreshToken() {
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtProvider.getUsername(REFRESH_TOKEN)).thenReturn(EMAIL);
        when(refreshTokenStore.matches(EMAIL, REFRESH_TOKEN)).thenReturn(true);
        org.mockito.Mockito.lenient().when(memberRepository.findForAccountUpdateByEmail(EMAIL))
                .thenAnswer(invocation -> memberRepository.findByEmail(EMAIL));
    }

    @Test
    void 비밀번호_없는_회원도_재발급받고_최신_역할이_담긴다() {
        validRefreshToken();
        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(kakaoMember(MemberRole.PREMIUM, MemberStatus.ACTIVE)));
        when(jwtProvider.createAccessToken(EMAIL, "PREMIUM")).thenReturn("new-access");

        LoginResponse response = authService.refresh(REFRESH_TOKEN);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    void 역할이_비어_있으면_USER로_재발급한다() {
        validRefreshToken();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(kakaoMember(null, MemberStatus.ACTIVE)));
        when(jwtProvider.createAccessToken(EMAIL, "USER")).thenReturn("new-access");

        assertThat(authService.refresh(REFRESH_TOKEN).accessToken()).isEqualTo("new-access");
    }

    @Test
    void 재발급_중_회원이_사라졌으면_인증_실패로_처리한다() {
        validRefreshToken();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN)).isInstanceOf(UsernameNotFoundException.class);
        verify(jwtProvider, never()).createAccessToken(any(), any());
    }

    @Test
    void 정지된_회원은_재발급하지_않고_저장된_토큰을_지운다() {
        validRefreshToken();
        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(kakaoMember(MemberRole.USER, MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN)).isInstanceOf(LockedException.class);
        verify(refreshTokenStore).remove(EMAIL);
        verify(jwtProvider, never()).createAccessToken(any(), any());
    }

    @Test
    void 인증된_회원에게_토큰을_발급하고_리프레시_토큰을_저장한다() {
        Member member = kakaoMember(MemberRole.USER, MemberStatus.ACTIVE);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findForAccountUpdateByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(jwtProvider.createAccessToken(EMAIL, "USER")).thenReturn("access");
        when(jwtProvider.createRefreshToken(EMAIL)).thenReturn(REFRESH_TOKEN);

        LoginResponse response = authService.issueTokens(member);

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(refreshTokenStore).save(EMAIL, REFRESH_TOKEN);
    }

    @Test
    void 정지된_회원에게는_토큰을_발급하지_않는다() {
        Member member = kakaoMember(MemberRole.USER, MemberStatus.SUSPENDED);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findForAccountUpdateByEmail(EMAIL)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.issueTokens(member)).isInstanceOf(LockedException.class);
        verify(refreshTokenStore, never()).save(any(), any());
    }
}
