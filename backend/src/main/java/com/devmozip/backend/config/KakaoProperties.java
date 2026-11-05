package com.devmozip.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 카카오 로그인 설정. 값이 없어도 서버는 뜨고, isConfigured()가 false면 카카오 기능만 쓸 수 없다. */
@Component
@ConfigurationProperties(prefix = "kakao")
public class KakaoProperties {

    private String restApiKey;
    private String clientSecret;
    private String domainName;
    private String redirectPath;

    /** 카카오에 요청할 값이 모두 있는지 확인한다. Redirect URI를 만들려면 서비스 주소가 주소 형식이어야 한다. */
    public boolean isConfigured() {
        return StringUtils.hasText(restApiKey)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(redirectPath)
                && domainName != null
                && (domainName.startsWith("http://") || domainName.startsWith("https://"));
    }

    /** 카카오 콘솔에 등록한 Redirect URI. 주소와 경로를 슬래시 하나로 잇는다. */
    public String buildRedirectUri() {
        return domainName.replaceAll("/+$", "") + "/" + redirectPath.replaceAll("^/+", "");
    }

    // Getter, Setter
    public String getRestApiKey() {
        return restApiKey;
    }

    public void setRestApiKey(String restApiKey) {
        this.restApiKey = restApiKey;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public void setRedirectPath(String redirectPath) {
        this.redirectPath = redirectPath;
    }
}
