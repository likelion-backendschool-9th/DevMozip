package com.devmozip.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.auth.LoginRequest;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.dto.auth.PasswordResetMailRequest;
import com.devmozip.backend.dto.auth.PasswordResetRequest;
import com.devmozip.backend.dto.auth.RefreshTokenRequest;
import com.devmozip.backend.dto.auth.SignupOptionsResponse;
import com.devmozip.backend.dto.auth.SignupRequest;
import com.devmozip.backend.dto.auth.SignupResponse;
import com.devmozip.backend.service.AuthService;
import com.devmozip.backend.service.KakaoAuthService;
import com.devmozip.backend.service.MemberService;
import com.devmozip.backend.service.PasswordResetFlowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final MemberService memberService;
    private final KakaoAuthService kakaoAuthService;
    private final PasswordResetFlowService passwordResetFlowService;

    public AuthController(AuthService service, MemberService memberService, KakaoAuthService kakaoAuthService,
                          PasswordResetFlowService passwordResetFlowService) {
        this.service = service;
        this.memberService = memberService;
        this.kakaoAuthService = kakaoAuthService;
        this.passwordResetFlowService = passwordResetFlowService;
    }

    /** 회원가입. 이메일 가입은 프론트엔드가 가입 직후 로그인을 따로 호출하고, 카카오 가입은 토큰을 함께 돌려준다. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request){
        return StringUtils.hasText(request.kakaoSignupToken())
                ? kakaoAuthService.signup(request)
                : memberService.signup(request);
    }

    /** 회원가입 화면의 직군·기술 스택·관심 분야 선택지 */
    @GetMapping("/signup/options")
    public SignupOptionsResponse signupOptions(){
        return memberService.getSignupOptions();
    }

    /** 회원가입 화면의 이메일 중복 확인 */
    @GetMapping("/check-email")
    public Map<String, Boolean> checkEmail(@RequestParam String email){
        return Map.of("available", memberService.isEmailAvailable(email));
    }

    /** 회원가입 화면의 닉네임 중복 확인 */
    @GetMapping("/check-nickname")
    public Map<String, Boolean> checkNickname(@RequestParam String nickname){
        return Map.of("available", memberService.isNicknameAvailable(nickname));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest){
        LoginResponse tokenResponse = service.login(loginRequest);
        return tokenResponse;
    }

    /** 액세스 토큰이 만료됐을 때 리프레시 토큰으로 새 액세스 토큰을 받는다. */
    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshTokenRequest refreshTokenRequest){
        return service.refresh(refreshTokenRequest.refreshToken());
    }

    /** 리프레시 토큰을 폐기한다. 액세스 토큰은 남은 수명(최대 30분)까지만 유효하다. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshTokenRequest refreshTokenRequest){
        service.logout(refreshTokenRequest.refreshToken());
    }

    /** 재설정 메일 발송 요청. 가입 여부와 관계없이 같은 응답을 준다. 사용자 IP는 BFF가 X-Real-IP로 넘긴다. */
    @PostMapping("/password/reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetMailRequest request,
                                     @RequestHeader(name = "X-Real-IP", required = false) String clientIp){
        passwordResetFlowService.requestReset(request.email(), clientIp);
    }

    /** 메일 링크의 토큰으로 새 비밀번호를 저장한다. 대상 회원은 토큰으로만 정한다. */
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request){
        passwordResetFlowService.resetPassword(request);
    }
}
