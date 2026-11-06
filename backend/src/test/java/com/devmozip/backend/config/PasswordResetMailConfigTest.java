package com.devmozip.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.devmozip.backend.service.PasswordResetMailSender;
import com.devmozip.backend.service.impl.ConsolePasswordResetMailSender;
import com.devmozip.backend.service.impl.SmtpPasswordResetMailSender;

/** 환경별 발송 구현과 발송 스레드의 대기열·종료 동작을 확인한다. SMTP에는 연결하지 않는다. */
class PasswordResetMailConfigTest {

    private static final int MAX_THREADS = 4;
    private static final int QUEUE_CAPACITY = 100;

    private static ApplicationContextRunner runner(String mailHost) {
        return new ApplicationContextRunner()
                .withBean(PasswordResetProperties.class, () -> {
                    PasswordResetProperties properties = new PasswordResetProperties();
                    properties.setMailHost(mailHost);
                    return properties;
                })
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withUserConfiguration(PasswordResetMailConfig.class);
    }

    @Test
    void local_프로필에_MAIL_HOST가_없으면_콘솔_구현을_쓰고_요청을_받는다() {
        runner("").withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).getBean(PasswordResetMailSender.class).isInstanceOf(ConsolePasswordResetMailSender.class);
            assertThat(context.getBean(PasswordResetMailSender.class).isAvailable()).isTrue();
        });
    }

    @Test
    void local_프로필도_MAIL_HOST가_있으면_실제로_발송한다() {
        runner("smtp.test").withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).getBean(PasswordResetMailSender.class).isInstanceOf(SmtpPasswordResetMailSender.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "test", "prod"})
    void local_외_프로필은_MAIL_HOST가_없어도_콘솔_구현을_쓰지_않고_요청을_거절한다(String profile) {
        runner("").withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).getBean(PasswordResetMailSender.class).isInstanceOf(SmtpPasswordResetMailSender.class);
            assertThat(context.getBean(PasswordResetMailSender.class).isAvailable()).isFalse();
        });
    }

    @Test
    void 스레드와_대기열이_모두_차면_새_작업을_거절한다() {
        ThreadPoolTaskExecutor executor = executor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < MAX_THREADS + QUEUE_CAPACITY; i++) {
                executor.execute(() -> await(release));
            }

            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(TaskRejectedException.class);
        } finally {
            release.countDown();
            executor.destroy();
        }
    }

    @Test
    void 서버를_종료할_때_진행_중이거나_대기_중인_발송을_마친다() throws Exception {
        ThreadPoolTaskExecutor executor = executor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        executor.execute(() -> {
            started.countDown();
            pause();
            completed.incrementAndGet();
        });
        executor.execute(() -> {
            pause();
            completed.incrementAndGet();
        });
        executor.execute(completed::incrementAndGet);
        started.await();

        executor.destroy();

        assertThat(completed).hasValue(3);
    }

    private static ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new PasswordResetMailConfig().passwordResetMailExecutor();
        executor.afterPropertiesSet();
        return executor;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 종료를 기다리지 않으면 이 대기가 중단되어 완료 수가 모자라게 된다
    private static void pause() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
