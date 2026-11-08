package com.devmozip.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.devmozip.backend.service.PasswordResetMailSender;
import com.devmozip.backend.service.impl.ConsolePasswordResetMailSender;
import com.devmozip.backend.service.impl.SmtpPasswordResetMailSender;

/**
 * application.yaml의 메일 설정이 기동과 헬스체크에 주는 영향을 실제 설정으로 확인한다.
 * 접속할 수 없는 주소만 쓰므로 실제 메일 발송 검증이 아니다.
 */
class MailConfigurationIntegrationTest {

    // 개발자 PC의 환경변수와 관계없이 같은 환경을 재현한다
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "jwt.secret=test-only-secret-key-at-least-32-bytes!!",
            "spring.datasource.url=jdbc:h2:mem:mail-configuration;MODE=PostgreSQL",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "management.health.redis.enabled=false",
            "MAIL_HOST=",
            "MAIL_PORT=587",
            "MAIL_USERNAME=",
            "MAIL_PASSWORD=",
            "MAIL_FROM="
    })
    abstract static class MailContext {
        @Autowired MockMvc mvc;
        @Autowired ApplicationContext context;
        @Autowired PasswordResetProperties properties;
        @Autowired JavaMailSender mailSender;
        @Autowired PasswordResetMailSender passwordResetMailSender;
    }

    // 포트 1은 연결이 곧바로 거절되므로 SMTP 장애를 재현한다
    static final String UNREACHABLE_HOST = "MAIL_HOST=127.0.0.1";
    static final String UNREACHABLE_PORT = "MAIL_PORT=1";

    static class 메일_값이_없으면 extends MailContext {

        @Test
        void 서버가_뜨고_헬스체크가_UP이며_발송만_꺼진다() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
            assertThat(properties.isConfigured()).isFalse();
            assertThat(passwordResetMailSender).isInstanceOf(SmtpPasswordResetMailSender.class);
            assertThat(passwordResetMailSender.isAvailable()).isFalse();
        }

        @Test
        void 발송_스레드를_추가해도_스프링_부트의_기본_스레드가_유지된다() {
            assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
            assertThat(context.getBean("passwordResetMailExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        }

        @Test
        void 제한_값과_SMTP_시간_제한을_읽는다() {
            assertThat(properties.getTokenTtl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.getEmailCooldown()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.getEmailHourlyLimit()).isEqualTo(5);
            assertThat(properties.getIpHourlyLimit()).isEqualTo(20);

            JavaMailSenderImpl sender = (JavaMailSenderImpl) mailSender;
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsEntry("mail.smtp.connectiontimeout", "5000")
                    .containsEntry("mail.smtp.timeout", "5000")
                    .containsEntry("mail.smtp.writetimeout", "5000");
        }
    }

    // 개발자 PC의 Git 제외 초기 데이터(members_data.sql)와 관계없이 메일 설정만 확인한다
    @ActiveProfiles("local")
    @TestPropertySource(properties = "spring.sql.init.mode=never")
    static class local_프로필에_메일_값이_없으면 extends MailContext {

        @Test
        void 콘솔_구현으로_요청을_받고_서비스_주소는_기본값을_쓴다() {
            assertThat(passwordResetMailSender).isInstanceOf(ConsolePasswordResetMailSender.class);
            assertThat(passwordResetMailSender.isAvailable()).isTrue();
            assertThat(properties.getBaseUrl()).isEqualTo(System.getenv().getOrDefault("DOMAINNAME", "http://localhost:3000"));
        }
    }

    @TestPropertySource(properties = {
            UNREACHABLE_HOST, UNREACHABLE_PORT,
            "MAIL_USERNAME=user", "MAIL_PASSWORD=secret", "MAIL_FROM=from@service.test", "DOMAINNAME=https://service.test"
    })
    static class 접속할_수_없는_SMTP를_지정해도 extends MailContext {

        @Test
        void 헬스체크는_UP이고_발송_설정은_켜진다() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
            assertThat(properties.isConfigured()).isTrue();
        }
    }

    // 위 테스트가 SMTP에 실제로 영향받지 않아서 통과하는 것이 아님을 보이는 대조군이다
    @TestPropertySource(properties = {UNREACHABLE_HOST, UNREACHABLE_PORT, "management.health.mail.enabled=true"})
    static class 메일_헬스체크를_켜면 extends MailContext {

        @Test
        void 접속할_수_없는_SMTP가_헬스체크를_DOWN으로_만든다() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("DOWN"));
        }
    }
}
