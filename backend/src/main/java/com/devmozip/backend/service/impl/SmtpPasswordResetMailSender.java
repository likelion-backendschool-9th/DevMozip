package com.devmozip.backend.service.impl;

import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetMailSender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/** SMTP로 메일을 보낸다. 동적 값은 서버가 만든 링크뿐이므로 템플릿 엔진 없이 문자열로 만든다. */
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailSender.class);

    private static final String SENDER_NAME = "DevMozip";
    private static final String SUBJECT = "[DevMozip] 비밀번호 재설정 안내";

    private static final String HTML = """
            <div style="max-width:480px;margin:0 auto;padding:32px 24px;font-family:sans-serif;color:#1f2933;line-height:1.6">
              <h1 style="margin:0 0 16px;font-size:20px;color:#7955df">DevMozip</h1>
              <p style="margin:0 0 24px">%s</p>
              <p style="margin:0 0 24px"><a href="%s" style="display:inline-block;padding:12px 20px;border-radius:8px;background:#7955df;color:#ffffff;text-decoration:none;font-weight:600">%s</a></p>
              <p style="margin:0;font-size:13px;color:#6b7280">%s</p>
            </div>
            """;

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    public SmtpPasswordResetMailSender(JavaMailSender mailSender, PasswordResetProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    @Override
    public void sendResetLink(String to, String token) {
        String intro = "비밀번호 재설정을 요청하셨습니다. 아래 링크에서 %d분 안에 새 비밀번호를 설정해 주세요. 링크는 한 번만 사용할 수 있습니다."
                .formatted(properties.getTokenTtl().toMinutes());
        String note = "직접 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.";
        send(to, intro, properties.buildResetLink(token), "비밀번호 재설정", note);
    }

    @Override
    public void sendKakaoGuide(String to) {
        String intro = "이 이메일은 카카오 로그인으로 가입한 계정이라 재설정할 비밀번호가 없습니다. 카카오로 로그인한 뒤 설정 화면에서 비밀번호를 정하면 이메일로도 로그인할 수 있습니다.";
        String note = "직접 요청하지 않았다면 이 메일을 무시해 주세요. 계정은 바뀌지 않습니다.";
        send(to, intro, properties.buildKakaoGuideLink(), "로그인하고 비밀번호 정하기", note);
    }

    private void send(String to, String intro, String link, String buttonLabel, String note) {
        String text = intro + "\n\n" + link + "\n\n" + note;
        String html = HTML.formatted(intro, HtmlUtils.htmlEscape(link), buttonLabel, note);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getFromAddress(), SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            // 예외 메시지에 수신 주소가 들어갈 수 있어 클래스 이름만 남긴다
            log.warn("비밀번호 재설정 메일 발송 실패: {}", e.getClass().getSimpleName());
        }
    }
}
