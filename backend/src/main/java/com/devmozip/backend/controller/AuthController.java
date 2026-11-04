package com.devmozip.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.auth.LoginRequest;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.dto.auth.RefreshTokenRequest;
import com.devmozip.backend.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    
    public AuthController(AuthService service) {
        this.service = service;
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
