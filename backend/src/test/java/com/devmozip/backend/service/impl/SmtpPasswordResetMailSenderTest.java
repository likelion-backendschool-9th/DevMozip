package com.devmozip.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.devmozip.backend.config.PasswordResetProperties;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** JavaMailSender를 가짜로 바꿔 메일 내용만 확인하므로 실제 발송 검증이 아니다. */
@ExtendWith(OutputCaptureExtension.class)
class SmtpPasswordResetMailSenderTest {

    private static final String TO = "member@devmozip.test";
    private static final String FROM = "noreply@devmozip.test";
    private static final String TOKEN = "abc-DEF_123";
    private static final String SUBJECT = "[DevMozip] 비밀번호 재설정 안내";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PasswordResetProperties properties = new PasswordResetProperties();
    private final SmtpPasswordResetMailSender sender = new SmtpPasswordResetMailSender(mailSender, properties);
    private final Session session = Session.getInstance(new Properties());

    @BeforeEach
    void setUp() {
        properties.setFromAddress(FROM);
        properties.setBaseUrl("https://service.test");
        properties.setTokenTtl(Duration.ofMinutes(30));
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(session));
    }

    @Test
    void 재설정_메일은_수신자_발신자_제목이_맞다() throws Exception {
        sender.sendResetLink(TO, TOKEN);

        MimeMessage message = sentMessage();
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly(TO);
        assertThat(message.getSubject()).isEqualTo(SUBJECT);
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo(FROM);
        assertThat(from.getPersonal()).isEqualTo("DevMozip");
    }

    @Test
    void 재설정_메일의_텍스트와_HTML에_같은_링크와_유효_시간이_있다() throws Exception {
        sender.sendResetLink(TO, TOKEN);

        MimeMessage message = sentMessage();
        String link = "https://service.test/forgot-password#token=" + TOKEN;
        assertThat(body(message, "text/plain")).contains(link).contains("30분");
        assertThat(body(message, "text/html")).contains("href=\"" + link + "\"").contains("30분");
    }

    @Test
    void 카카오_안내_메일에는_토큰이_없고_로그인_링크가_있다() throws Exception {
        sender.sendKakaoGuide(TO);

        MimeMessage message = sentMessage();
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly(TO);
        assertThat(message.getSubject()).isEqualTo(SUBJECT);
        String link = "https://service.test/login?next=/settings/privacy";
        assertThat(body(message, "text/plain")).contains(link).contains("카카오").doesNotContain("token");
        assertThat(body(message, "text/html")).contains("href=\"" + link + "\"").contains("카카오").doesNotContain("token");
    }

    @Test
    void 발송에_실패해도_예외를_던지지_않고_수신_주소를_로그에_남기지_않는다(CapturedOutput output) {
        doThrow(new MailSendException("failed for " + TO)).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> sender.sendResetLink(TO, TOKEN)).doesNotThrowAnyException();

        assertThat(output).contains("MailSendException").doesNotContain(TO).doesNotContain(TOKEN);
    }

    @Test
    void 잘못된_수신_주소도_예외를_던지지_않고_주소를_로그에_남기지_않는다(CapturedOutput output) {
        assertThatCode(() -> sender.sendResetLink("not an address@", TOKEN)).doesNotThrowAnyException();

        assertThat(output).contains("AddressException").doesNotContain("not an address").doesNotContain(TOKEN);
    }

    @Test
    void 발송에_성공해도_링크를_로그에_남기지_않는다(CapturedOutput output) {
        sender.sendResetLink(TO, TOKEN);

        assertThat(output).doesNotContain(TOKEN).doesNotContain(TO);
    }

    @Test
    void 발송_설정이_없으면_요청을_받을_수_없다() {
        assertThat(sender.isAvailable()).isFalse();

        properties.setMailHost("smtp.test");
        properties.setMailUsername("user");
        properties.setMailPassword("secret");
        assertThat(sender.isAvailable()).isTrue();
    }

    // 실제 발송처럼 메일 형식으로 직렬화했다가 다시 읽어 인코딩까지 확인한다
    private MimeMessage sentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        captor.getValue().writeTo(out);
        return new MimeMessage(session, new ByteArrayInputStream(out.toByteArray()));
    }

    private static String body(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String body = body(multipart.getBodyPart(i), mimeType);
                if (body != null) {
                    return body;
                }
            }
        }
        return null;
    }
}
