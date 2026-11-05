package com.devmozip.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 비밀번호 재설정 설정. 값이 없어도 서버는 뜨고, isConfigured()가 false면 메일을 보낼 수 없다. */
@Component
@ConfigurationProperties(prefix = "password-reset")
public class PasswordResetProperties {

    private String mailHost;
    private String mailUsername;
    private String mailPassword;
    private String fromAddress;
    private String baseUrl;
    private Duration tokenTtl;
    private Duration emailCooldown;
    private int emailHourlyLimit;
    private int ipHourlyLimit;

    /** 메일 발송에 필요한 값이 모두 있는지 확인한다. 메일 링크를 만들려면 서비스 주소가 주소 형식이어야 한다. */
    public boolean isConfigured() {
        return StringUtils.hasText(mailHost)
                && StringUtils.hasText(mailUsername)
                && StringUtils.hasText(mailPassword)
                && StringUtils.hasText(fromAddress)
                && baseUrl != null
                && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"));
    }

    /** 재설정 메일의 링크. 토큰은 서버로 전송되지 않는 URL fragment에 싣는다. */
    public String buildResetLink(String token) {
        return trimmedBaseUrl() + "/forgot-password#token=" + token;
    }

    /** 카카오 회원 안내 메일의 링크. 로그인하면 비밀번호를 정하는 화면으로 이동한다. */
    public String buildKakaoGuideLink() {
        return trimmedBaseUrl() + "/login?next=/settings/privacy";
    }

    private String trimmedBaseUrl() {
        return baseUrl.replaceAll("/+$", "");
    }

    // Getter, Setter
    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Duration getEmailCooldown() {
        return emailCooldown;
    }

    public void setEmailCooldown(Duration emailCooldown) {
        this.emailCooldown = emailCooldown;
    }

    public int getEmailHourlyLimit() {
        return emailHourlyLimit;
    }

    public void setEmailHourlyLimit(int emailHourlyLimit) {
        this.emailHourlyLimit = emailHourlyLimit;
    }

    public int getIpHourlyLimit() {
        return ipHourlyLimit;
    }

    public void setIpHourlyLimit(int ipHourlyLimit) {
        this.ipHourlyLimit = ipHourlyLimit;
    }
}
