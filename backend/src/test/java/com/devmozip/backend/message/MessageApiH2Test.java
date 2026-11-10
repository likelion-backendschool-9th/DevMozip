package com.devmozip.backend.message;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/** local 프로필과 같은 H2 설정에서 메시지 API가 동작하는지 확인한다. Docker가 없어도 실행된다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:message-api",
	"spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageApiH2Test extends MessageApiTests {
}
