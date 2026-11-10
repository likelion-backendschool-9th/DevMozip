package com.devmozip.backend.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 메신저 WebSocket 설정. 시간 값은 nginx 타임아웃(75초)보다 짧게 둔다. */
@Component
@ConfigurationProperties(prefix = "messenger.realtime")
@Getter
@Setter
public class MessengerRealtimeProperties {
	private String allowedOrigin = "";
	private Duration pingInterval = Duration.ofSeconds(30);
	private Duration pongTimeout = Duration.ofSeconds(70);
	private Duration checkInterval = Duration.ofSeconds(1);
	private Duration sendTimeLimit = Duration.ofSeconds(5);
	/** 회원·방당 입력 중 신호를 받는 최소 간격. 더 자주 오면 무시한다. 클라이언트는 3초에 한 번 보낸다. */
	private Duration typingInterval = Duration.ofSeconds(1);
	/** 입력 중 신호의 참여 여부를 연결마다 기억하는 시간. 그동안의 퇴장·삭제·정지는 이만큼 늦게 반영된다. */
	private Duration typingAccessCache = Duration.ofSeconds(10);

	/** 끝의 슬래시를 뺀 허용 Origin. 값이 없으면 null이다. */
	public String normalizedOrigin() {
		if (!StringUtils.hasText(allowedOrigin)) {
			return null;
		}
		return allowedOrigin.trim().replaceAll("/+$", "");
	}

	public boolean allowsOrigin(String origin) {
		String allowed = normalizedOrigin();
		return allowed != null && allowed.equalsIgnoreCase(origin);
	}
}
