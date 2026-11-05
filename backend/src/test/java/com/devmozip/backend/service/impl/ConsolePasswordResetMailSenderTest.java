package com.devmozip.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.devmozip.backend.config.PasswordResetProperties;

@ExtendWith(OutputCaptureExtension.class)
class ConsolePasswordResetMailSenderTest {

    private static final String TO = "member@devmozip.test";

    private final PasswordResetProperties properties = new PasswordResetProperties();
    private final ConsolePasswordResetMailSender sender = new ConsolePasswordResetMailSender(properties);

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("http://localhost:3000");
    }

    @Test
    void SMTP_값이_없어도_요청을_받는다() {
        assertThat(sender.isAvailable()).isTrue();
    }

    @Test
    void 재설정_링크를_출력한다(CapturedOutput output) {
        sender.sendResetLink(TO, "abc-DEF_123");

        assertThat(output).contains(TO).contains("http://localhost:3000/forgot-password#token=abc-DEF_123");
    }

    @Test
    void 카카오_회원에게는_토큰_없는_로그인_링크를_출력한다(CapturedOutput output) {
        sender.sendKakaoGuide(TO);

        assertThat(output).contains(TO).contains("http://localhost:3000/login?next=/settings/privacy").doesNotContain("token");
    }
}
