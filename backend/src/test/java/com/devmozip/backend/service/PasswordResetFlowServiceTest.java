package com.devmozip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.RefreshTokenStore;

/** 요청 처리의 순서와 실패 동작을 확인한다. 저장소·메일·DB는 모두 가짜다. */
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetFlowServiceTest {

    private static final String EMAIL = "member@devmozip.test";
    private static final String IP = "203.0.113.10";

    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final PasswordResetMailSender mailSender = mock(PasswordResetMailSender.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);

    @BeforeEach
    void setUp() {
        when(mailSender.isAvailable()).thenReturn(true);
        when(passwordResetService.tryAcquireIp(anyString())).thenReturn(true);
        when(passwordResetService.tryAcquireEmail(anyString())).thenReturn(true);
    }

    private PasswordResetFlowService service(TaskExecutor executor) {
        return new PasswordResetFlowService(passwordResetService, mailSender, executor, memberRepository,
                mock(MemberService.class), mock(PasswordEncoder.class), mock(RefreshTokenStore.class));
    }

    private void memberWithPassword() {
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(Member.builder()
                .email(EMAIL).passwordHash("hash").status(MemberStatus.ACTIVE).build()));
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void 응답하기_전에는_회원을_조회하지_않는다() {
        List<Runnable> tasks = new ArrayList<>();
        memberWithPassword();

        service(tasks::add).requestReset(EMAIL, IP);

        verifyNoInteractions(memberRepository);
        assertThat(tasks).hasSize(1);

        tasks.get(0).run();
        verify(memberRepository).findByEmail(EMAIL);
    }

    @Test
    void 이메일은_소문자로_바꾸고_앞뒤_공백을_지워_처리한다() {
        memberWithPassword();

        service(new SyncTaskExecutor()).requestReset("  Member@DevMozip.Test ", IP);

        verify(passwordResetService).tryAcquireEmail(EMAIL);
        verify(mailSender).sendResetLink(eq(EMAIL), anyString());
    }

    @Test
    void 저장소에는_토큰_해시만_넘기고_메일에는_원문을_넘긴다() throws Exception {
        memberWithPassword();

        service(new SyncTaskExecutor()).requestReset(EMAIL, IP);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendResetLink(eq(EMAIL), token.capture());
        verify(passwordResetService).saveToken(eq(EMAIL), hash.capture());
        assertThat(token.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(hash.getValue()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getValue().getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void 요청마다_다른_토큰을_만든다() {
        memberWithPassword();
        PasswordResetFlowService service = service(new SyncTaskExecutor());

        service.requestReset(EMAIL, IP);
        service.requestReset(EMAIL, IP);

        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(mailSender, times(2)).sendResetLink(eq(EMAIL), tokens.capture());
        assertThat(tokens.getAllValues().get(0)).isNotEqualTo(tokens.getAllValues().get(1));
    }

    @Test
    void 메일을_보낼_수_없으면_503이고_아무것도_세지_않는다() {
        when(mailSender.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service(new SyncTaskExecutor()).requestReset(EMAIL, IP))
                .satisfies(thrown -> assertStatus(thrown, HttpStatus.SERVICE_UNAVAILABLE));

        verifyNoInteractions(passwordResetService, memberRepository);
    }

    @Test
    void 대기열이_가득_차면_503이다() {
        TaskExecutor rejecting = task -> {
            throw new TaskRejectedException("full");
        };

        assertThatThrownBy(() -> service(rejecting).requestReset(EMAIL, IP))
                .satisfies(thrown -> assertStatus(thrown, HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void IP_제한을_넘으면_429이고_발송_작업을_맡기지_않는다() {
        when(passwordResetService.tryAcquireIp(IP)).thenReturn(false);

        assertThatThrownBy(() -> service(new SyncTaskExecutor()).requestReset(EMAIL, IP))
                .satisfies(thrown -> assertStatus(thrown, HttpStatus.TOO_MANY_REQUESTS));

        verify(passwordResetService, never()).tryAcquireEmail(any());
        verifyNoInteractions(memberRepository);
    }

    @Test
    void IP_헤더가_없으면_IP_제한만_건너뛰고_이메일_제한은_적용한다() {
        when(passwordResetService.tryAcquireEmail(EMAIL)).thenReturn(false);

        service(new SyncTaskExecutor()).requestReset(EMAIL, null);

        verify(passwordResetService, never()).tryAcquireIp(any());
        verify(passwordResetService).tryAcquireEmail(EMAIL);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 처리_중_실패해도_요청은_성공하고_이메일과_토큰을_로그에_남기지_않는다(CapturedOutput output) {
        memberWithPassword();
        doThrow(new IllegalStateException("redis failed for " + EMAIL))
                .when(passwordResetService).saveToken(eq(EMAIL), anyString());

        assertThatCode(() -> service(new SyncTaskExecutor()).requestReset(EMAIL, IP)).doesNotThrowAnyException();

        verify(mailSender, never()).sendResetLink(any(), any());
        assertThat(output).contains("IllegalStateException").doesNotContain(EMAIL);
    }
}
