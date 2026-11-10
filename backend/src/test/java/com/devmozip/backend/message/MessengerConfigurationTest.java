package com.devmozip.backend.message;

import java.time.Clock;
import java.time.ZoneId;
import com.devmozip.backend.config.MessengerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessengerConfigurationTest {
	@Test void clockUsesSeoulWhateverTheServerTimeZoneIs() {
		new ApplicationContextRunner().withUserConfiguration(MessengerConfiguration.class).run(context ->
			assertEquals(ZoneId.of("Asia/Seoul"), context.getBean("messengerClock", Clock.class).getZone()));
	}
}
