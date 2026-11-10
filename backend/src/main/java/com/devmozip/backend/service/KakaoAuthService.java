package com.devmozip.backend.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.client.KakaoClient;
import com.devmozip.backend.dto.auth.KakaoAuthorizeResponse;
import com.devmozip.backend.dto.auth.KakaoCallbackResponse;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.dto.auth.SignupRequest;
import com.devmozip.backend.dto.auth.SignupResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InvalidTokenException;
import com.devmozip.backend.security.jwt.JwtProvider;

/** 카카오 로그인과 카카오 가입. 카카오 호출이 DB 트랜잭션을 붙잡지 않도록 여기에는 트랜잭션을 걸지 않는다. */
@Service
public class KakaoAuthService {

    private static final String LOGIN = "login";
    private static final String SIGNUP = "signup";

    private final KakaoClient kakaoClient;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final AuthService authService;
    private final JwtProvider jwtProvider;

    public KakaoAuthService(KakaoClient kakaoClient,
                            MemberRepository memberRepository,
                            MemberService memberService,
                            AuthService authService,
                            JwtProvider jwtProvider) {
        this.kakaoClient = kakaoClient;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.authService = authService;
        this.jwtProvider = jwtProvider;
    }

    public KakaoAuthorizeResponse authorize(String state) {
        return new KakaoAuthorizeResponse(kakaoClient.authorizeUrl(state));
    }

    /**
     * 받은 카카오 회원번호로 회원을 찾는다.
     * 회원이 없으면 가입 토큰을 돌려주고, 있으면 로그인에서 시작한 경우에만 토큰을 발급한다.
     */
    public KakaoCallbackResponse handleCallback(String code, String intent) {
        // 인가 코드는 한 번만 쓸 수 있으므로 카카오를 호출하기 전에 확인한다.
        if (!LOGIN.equals(intent) && !SIGNUP.equals(intent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        String kakaoId = kakaoClient.fetchKakaoId(kakaoClient.requestAccessToken(code));
        Optional<Member> member = memberRepository.findByKakaoId(kakaoId);

        if (member.isEmpty()) {
            return KakaoCallbackResponse.signupRequired(jwtProvider.createKakaoSignupToken(kakaoId));
        }
        if (SIGNUP.equals(intent)) {
            return KakaoCallbackResponse.alreadyRegistered();
        }
        return KakaoCallbackResponse.login(authService.issueTokens(member.get()));
    }

    /** 로그인한 회원에 인가 코드로 확인한 카카오 계정을 연동한다. */
    public void link(String email, String code) {
        memberService.checkKakaoLinkable(email);

        String kakaoId = kakaoClient.fetchKakaoId(kakaoClient.requestAccessToken(code));

        memberService.linkKakao(email, kakaoId);
    }

    /** 다시 받은 인증이 연동한 카카오 계정인지 확인한 뒤, 카카오 연결을 끊고 kakao_id를 비운다. */
    public void unlink(String email, String code) {
        String linkedKakaoId = memberService.getUnlinkableKakaoId(email);

        String accessToken = kakaoClient.requestAccessToken(code);
        if (!linkedKakaoId.equals(kakaoClient.fetchKakaoId(accessToken))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "연동한 카카오 계정으로 인증해 주세요.");
        }

        kakaoClient.unlink(accessToken);
        memberService.unlinkKakao(email);
    }

    /** 가입 토큰에서 확인한 카카오 회원번호로 가입한다. 비밀번호가 없으므로 토큰을 바로 발급한다. */
    public SignupResponse signup(SignupRequest request) {
        String token = request.kakaoSignupToken();
        if (!jwtProvider.validateKakaoSignupToken(token)) {
            throw new InvalidTokenException("카카오 인증이 만료되었습니다. 다시 시도해 주세요.");
        }

        Member member = memberService.signupWithKakao(jwtProvider.getKakaoId(token), request);
        LoginResponse tokens = authService.issueTokens(member);

        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname(),
                tokens.accessToken(), tokens.refreshToken(), tokens.tokenType());
    }
}
