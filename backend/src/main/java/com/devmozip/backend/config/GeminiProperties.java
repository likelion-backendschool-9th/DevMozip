package com.devmozip.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 비밀값을 출력하는 toString을 만들지 않는다. 키가 없어도 기존 AI 기능은 실행 가능하다. */
@ConfigurationProperties("ai.gemini")
public class GeminiProperties {
	private String apiKey = "";
	private String model = "gemini-3.5-flash-lite";
	private Duration connectTimeout = Duration.ofSeconds(3);
	private Duration timeout = Duration.ofSeconds(25);
	private int maxOutputTokens = 4096;
	public String getApiKey() { return apiKey; }
	public void setApiKey(String value) { apiKey = value; }
	public String getModel() { return model; }
	public void setModel(String value) { model = value; }
	public Duration getConnectTimeout() { return connectTimeout; }
	public void setConnectTimeout(Duration value) { connectTimeout = value; }
	public Duration getTimeout() { return timeout; }
	public void setTimeout(Duration value) { timeout = value; }
	public int getMaxOutputTokens() { return maxOutputTokens; }
	public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
}
