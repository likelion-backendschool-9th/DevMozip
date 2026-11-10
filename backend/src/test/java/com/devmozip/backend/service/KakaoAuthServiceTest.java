package com.devmozip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.client.KakaoClient;
import com.devmozip.backend.dto.auth.KakaoCallbackResponse;
import com.devmozip.backend.dto.auth.KakaoCallbackResponse.Status;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.dto.auth.SignupRequest;
import com.devmozip.backend.dto.auth.SignupResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InvalidTokenException;
import com.devmozip.backend.security.jwt.JwtProvider;

/** 카카오 콜백과 가입의 분기를 DB·카카오 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class KakaoAuthServiceTest {

    private static final String EMAIL = "member@devmozip.test";
    private static final String CODE = "code";
    private static final String KAKAO_ID = "123456789";
    private static final LoginResponse TOKENS = LoginResponse.builder()
            .accessToken("access").refreshToken("refresh").tokenType("Bearer").build();
    private static final SignupRequest SIGNUP_REQUEST = new SignupRequest("new@devmozip.test", null, null, "새회원",
            "백엔드 개발자", "3년 차", List.of("Java"), List.of("AI"), true, "signup-token");

    @Mock
    private KakaoClient kakaoClient;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberService memberService;
    @Mock
    private AuthService authService;
    @Mock
    private JwtProvider jwtProvider;
    @InjectMocks
    private KakaoAuthService kakaoAuthService;

    private void kakaoAnswers() {
        when(kakaoClient.requestAccessToken(CODE)).thenReturn("kakao-access");
        when(kakaoClient.fetchKakaoId("kakao-access")).thenReturn(KAKAO_ID);
    }

    @Test
    void 로그인에서_등록된_회원이면_토큰을_발급한다() {
        kakaoAnswers();
        Member member = Member.builder().email("kakao@devmozip.test").build();
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(member));
        when(authService.issueTokens(member)).thenReturn(TOKENS);

        KakaoCallbackResponse response = kakaoAuthService.handleCallback(CODE, "login");

        assertThat(response.status()).isEqualTo(Status.LOGIN);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.kakaoSignupToken()).isNull();
    }

    @Test
    void 등록되지_않은_카카오_계정이면_가입_토큰을_돌려준다() {
        kakaoAnswers();
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.empty());
        when(jwtProvider.createKakaoSignupToken(KAKAO_ID)).thenReturn("signup-token");

        for (String intent : List.of("login", "signup")) {
            KakaoCallbackResponse response = kakaoAuthService.handleCallback(CODE, intent);

            assertThat(response.status()).isEqualTo(Status.SIGNUP_REQUIRED);
            assertThat(response.kakaoSignupToken()).isEqualTo("signup-token");
            assertThat(response.accessToken()).isNull();
        }
        verifyNoInteractions(authService);
    }

    @Test
    void 가입에서_이미_등록된_회원이면_로그인시키지_않는다() {
        kakaoAnswers();
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(Member.builder().build()));

        KakaoCallbackResponse response = kakaoAuthService.handleCallback(CODE, "signup");

        assertThat(response.status()).isEqualTo(Status.ALREADY_REGISTERED);
        assertThat(response.accessToken()).isNull();
        assertThat(response.kakaoSignupToken()).isNull();
        verifyNoInteractions(authService);
    }

    @Test
    void 정지된_회원의_카카오_로그인은_거절한다() {
        kakaoAnswers();
        Member member = Member.builder().build();
        when(memberRepository.findByKakaoId(KAKAO_ID)).thenReturn(Optional.of(member));
        when(authService.issueTokens(member)).thenThrow(new LockedException("정지"));

        assertThatThrownBy(() -> kakaoAuthService.handleCallback(CODE, "login")).isInstanceOf(LockedException.class);
    }

    @Test
    void 알_수_없는_의도면_인가_코드를_쓰기_전에_400() {
        assertThatThrownBy(() -> kakaoAuthService.handleCallback(CODE, "unknown"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(kakaoClient);
    }

    @Test
    void 연동할_수_없는_회원이면_인가_코드를_쓰지_않는다() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT)).when(memberService).checkKakaoLinkable(EMAIL);

        assertThatThrownBy(() -> kakaoAuthService.link(EMAIL, CODE)).isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(kakaoClient);
        verify(memberService, never()).linkKakao(any(), any());
    }

    @Test
    void 카카오에서_확인한_회원번호로_연동한다() {
        kakaoAnswers();

        kakaoAuthService.link(EMAIL, CODE);

        verify(memberService).linkKakao(EMAIL, KAKAO_ID);
    }

    @Test
    void 해제할_수_없는_회원이면_인가_코드를_쓰지_않는다() {
        when(memberService.getUnlinkableKakaoId(EMAIL)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> kakaoAuthService.unlink(EMAIL, CODE)).isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(kakaoClient);
        verify(memberService, never()).unlinkKakao(any());
    }

    @Test
    void 같은_카카오_계정이면_카카오_연결을_끊은_뒤_kakao_id를_비운다() {
        kakaoAnswers();
        when(memberService.getUnlinkableKakaoId(EMAIL)).thenReturn(KAKAO_ID);

        kakaoAuthService.unlink(EMAIL, CODE);

        InOrder order = inOrder(kakaoClient, memberService);
        order.verify(kakaoClient).unlink("kakao-access");
        order.verify(memberService).unlinkKakao(EMAIL);
    }

    @Test
    void 다른_카카오_계정이면_연결을_끊지_않는다() {
        kakaoAnswers();
        when(memberService.getUnlinkableKakaoId(EMAIL)).thenReturn("987654321");

        assertThatThrownBy(() -> kakaoAuthService.unlink(EMAIL, CODE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(kakaoClient, never()).unlink(any());
        verify(memberService, never()).unlinkKakao(any());
    }

    @Test
    void 카카오_연결_끊기에_실패하면_kakao_id를_유지한다() {
        kakaoAnswers();
        when(memberService.getUnlinkableKakaoId(EMAIL)).thenReturn(KAKAO_ID);
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY)).when(kakaoClient).unlink("kakao-access");

        assertThatThrownBy(() -> kakaoAuthService.unlink(EMAIL, CODE)).isInstanceOf(ResponseStatusException.class);

        verify(memberService, never()).unlinkKakao(any());
    }

    @Test
    void 가입_토큰의_회원번호로_가입하고_토큰을_함께_돌려준다() {
        Member member = Member.builder().email("new@devmozip.test").nickname("새회원").build();
        when(jwtProvider.validateKakaoSignupToken("signup-token")).thenReturn(true);
        when(jwtProvider.getKakaoId("signup-token")).thenReturn(KAKAO_ID);
        when(memberService.signupWithKakao(KAKAO_ID, SIGNUP_REQUEST)).thenReturn(member);
        when(authService.issueTokens(member)).thenReturn(TOKENS);

        SignupResponse response = kakaoAuthService.signup(SIGNUP_REQUEST);

        assertThat(response.email()).isEqualTo("new@devmozip.test");
        assertThat(response.nickname()).isEqualTo("새회원");
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void 가입_토큰이_유효하지_않으면_회원을_만들지_않는다() {
        when(jwtProvider.validateKakaoSignupToken("signup-token")).thenReturn(false);

        assertThatThrownBy(() -> kakaoAuthService.signup(SIGNUP_REQUEST)).isInstanceOf(InvalidTokenException.class);

        verify(memberService, never()).signupWithKakao(any(), any());
        verifyNoInteractions(authService);
    }
}
