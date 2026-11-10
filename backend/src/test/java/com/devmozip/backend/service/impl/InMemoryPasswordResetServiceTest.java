package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetService;

class InMemoryPasswordResetServiceTest extends PasswordResetServiceContractTest {

    private final MutableClock clock = new MutableClock();

    @Override
    protected PasswordResetService createService(PasswordResetProperties properties) {
        return new InMemoryPasswordResetService(properties, clock);
    }

    @Override
    protected void advance(Duration duration) {
        clock.now = clock.now.plus(duration);
    }

    private static class MutableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-11-05T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
