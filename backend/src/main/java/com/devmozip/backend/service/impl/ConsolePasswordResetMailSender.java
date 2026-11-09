package com.devmozip.backend.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetMailSender;

/** local 프로필에서 SMTP 없이 재설정 흐름을 시험하도록 메일 대신 링크를 출력한다. 토큰이 로그에 남으므로 운영에 등록하지 않는다. */
public class ConsolePasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsolePasswordResetMailSender.class);

    private final PasswordResetProperties properties;

    public ConsolePasswordResetMailSender(PasswordResetProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sendResetLink(String to, String token) {
        log.info("[메일 미발송] {} 재설정 링크: {}", to, properties.buildResetLink(token));
    }

    @Override
    public void sendKakaoGuide(String to) {
        log.info("[메일 미발송] {} 카카오 회원 안내 링크: {}", to, properties.buildKakaoGuideLink());
    }
}
