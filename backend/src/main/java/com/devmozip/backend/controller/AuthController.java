package com.devmozip.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.auth.LoginRequest;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.dto.auth.RefreshTokenRequest;
import com.devmozip.backend.dto.auth.SignupRequest;
import com.devmozip.backend.dto.auth.SignupResponse;
import com.devmozip.backend.service.AuthService;
import com.devmozip.backend.service.MemberService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final MemberService memberService;

    public AuthController(AuthService service, MemberService memberService) {
        this.service = service;
        this.memberService = memberService;
    }

    /** 회원가입. 로그인은 프론트엔드가 가입 직후 따로 호출한다. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request){
        return memberService.signup(request);
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
}
