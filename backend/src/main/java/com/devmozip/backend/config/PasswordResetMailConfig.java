package com.devmozip.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import com.devmozip.backend.service.PasswordResetMailSender;
import com.devmozip.backend.service.impl.ConsolePasswordResetMailSender;
import com.devmozip.backend.service.impl.SmtpPasswordResetMailSender;

@Configuration
public class PasswordResetMailConfig {

    /**
     * 메일 발송 전용 스레드. 대기열이 차면 TaskRejectedException을 던져 503으로 바꿀 수 있다.
     * 일반 빈으로 등록하면 스프링 부트의 기본 applicationTaskExecutor가 사라지므로 이름으로만 주입받게 한다.
     */
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor passwordResetMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-reset-mail-");
        // 서버 종료 시 진행 중인 발송을 10초까지 기다린다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /** 콘솔 구현은 토큰을 로그에 남기므로 local 프로필이고 MAIL_HOST가 없을 때만 쓴다. */
    @Bean
    public PasswordResetMailSender passwordResetMailSender(Environment environment,
                                                           PasswordResetProperties properties,
                                                           JavaMailSender mailSender) {
        if (environment.acceptsProfiles(Profiles.of("local")) && !StringUtils.hasText(properties.getMailHost())) {
            return new ConsolePasswordResetMailSender(properties);
        }
        return new SmtpPasswordResetMailSender(mailSender, properties);
    }
}
