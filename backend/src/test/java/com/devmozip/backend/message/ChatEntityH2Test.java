package com.devmozip.backend.message;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * local 프로필과 같은 H2 설정에서 채팅 테이블이 동작하는지 확인한다. Docker가 없어도 실행된다.
 * application-local.yml은 Git에서 제외되어 CI에 없으므로 local 프로필을 직접 켜지 않는다.
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:chat-schema",
	"spring.datasource.driver-class-name=org.h2.Driver"
})
@ActiveProfiles("test")
class ChatEntityH2Test extends ChatEntityRoundTripTests {
}
