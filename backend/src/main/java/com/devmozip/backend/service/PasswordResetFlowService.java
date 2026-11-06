package com.devmozip.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.dto.auth.PasswordResetRequest;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InvalidTokenException;
import com.devmozip.backend.security.jwt.RefreshTokenStore;

/** 비밀번호를 모를 때 메일 링크로 재설정하는 흐름. 저장은 PasswordResetService, 발송은 PasswordResetMailSender가 맡는다. */
@Service
public class PasswordResetFlowService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetFlowService.class);

    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "링크가 만료되었거나 이미 사용되었습니다. 비밀번호 재설정을 다시 요청해 주세요.";
    private static final String UNAVAILABLE_MESSAGE = "지금은 비밀번호 재설정 메일을 보낼 수 없습니다. 잠시 후 다시 시도해 주세요.";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";

    private final SecureRandom secureRandom = new SecureRandom();
    private final PasswordResetService passwordResetService;
    private final PasswordResetMailSender mailSender;
    private final TaskExecutor mailExecutor;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    public PasswordResetFlowService(PasswordResetService passwordResetService,
                                    PasswordResetMailSender mailSender,
                                    @Qualifier("passwordResetMailExecutor") TaskExecutor mailExecutor,
                                    MemberRepository memberRepository,
                                    MemberService memberService,
                                    PasswordEncoder passwordEncoder,
                                    RefreshTokenStore refreshTokenStore) {
        this.passwordResetService = passwordResetService;
        this.mailSender = mailSender;
        this.mailExecutor = mailExecutor;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
    }

    /**
     * 재설정 메일 발송을 맡기고 바로 돌아온다.
     * 회원 조회는 별도 스레드에서 하므로 응답 내용과 응답 시간으로 가입 여부가 드러나지 않는다.
     */
    public void requestReset(String email, String clientIp) {
        if (!mailSender.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        // IP 헤더가 없으면 IP 제한만 건너뛴다. 이메일 제한은 발송 작업에서 그대로 적용한다.
        if (StringUtils.hasText(clientIp) && !passwordResetService.tryAcquireIp(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_MESSAGE);
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            mailExecutor.execute(() -> sendMail(normalizedEmail));
        } catch (TaskRejectedException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    /** 입력 규칙을 통과한 뒤에 토큰을 소비한다. 입력 실수로 링크가 소진되지 않게 하기 위해서다. */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        memberService.checkNewPassword(request.newPassword(), request.confirmPassword());
        if (!StringUtils.hasText(request.token())) {
            throw new InvalidTokenException(INVALID_TOKEN_MESSAGE);
        }

        String email = passwordResetService.consumeToken(hash(request.token()))
                .orElseThrow(() -> new InvalidTokenException(INVALID_TOKEN_MESSAGE));
        Member member = memberRepository.findForAccountUpdateByEmail(email)
                .orElseThrow(() -> new InvalidTokenException(INVALID_TOKEN_MESSAGE));
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new InvalidTokenException(INVALID_TOKEN_MESSAGE);
        }

        member.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        // 같은 트랜잭션에서 지운다. Redis가 실패하면 변경도 롤백되어 탈취된 세션이 남은 채 성공 처리되지 않는다.
        refreshTokenStore.remove(email);
    }

    private void sendMail(String email) {
        try {
            if (!passwordResetService.tryAcquireEmail(email)) {
                return;
            }
            memberRepository.findByEmail(email)
                    .filter(member -> member.getStatus() != MemberStatus.SUSPENDED)
                    .ifPresent(this::sendMail);
        } catch (RuntimeException e) {
            // 예외 메시지에 이메일이 들어갈 수 있어 클래스 이름만 남긴다
            log.warn("비밀번호 재설정 요청 처리 실패: {}", e.getClass().getSimpleName());
        }
    }

    private void sendMail(Member member) {
        if (member.getPasswordHash() != null) {
            String token = createToken();
            passwordResetService.saveToken(member.getEmail(), hash(token));
            mailSender.sendResetLink(member.getEmail(), token);
        } else if (member.getKakaoId() != null) {
            // 메일로 비밀번호를 만들게 하면 메일을 받는 사람이 카카오 회원의 계정을 가져갈 수 있어 안내만 보낸다
            mailSender.sendKakaoGuide(member.getEmail());
        }
    }

    private String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
