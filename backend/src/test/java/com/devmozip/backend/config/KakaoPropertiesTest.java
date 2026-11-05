package com.devmozip.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 값이 없거나 DOMAINNAME이 주소가 아닐 때 카카오 기능만 꺼지는지, Redirect URI를 정확히 만드는지 확인한다. */
class KakaoPropertiesTest {

    private static final String DOMAIN_NAME = "https://service.test";
    private static final String PATH = "api/auth/kakao/callback";

    private static KakaoProperties properties(String key, String secret, String domainName, String path) {
        KakaoProperties properties = new KakaoProperties();
        properties.setRestApiKey(key);
        properties.setClientSecret(secret);
        properties.setDomainName(domainName);
        properties.setRedirectPath(path);
        return properties;
    }

    @Test
    void 값이_모두_있으면_사용할_수_있다() {
        assertThat(properties("key", "secret", DOMAIN_NAME, PATH).isConfigured()).isTrue();
        assertThat(properties("key", "secret", "http://service.test:3000", PATH).isConfigured()).isTrue();
    }

    @Test
    void 값이_하나라도_없으면_설정되지_않은_것으로_본다() {
        assertThat(new KakaoProperties().isConfigured()).isFalse();
        assertThat(properties("", "secret", DOMAIN_NAME, PATH).isConfigured()).isFalse();
        assertThat(properties("key", "", DOMAIN_NAME, PATH).isConfigured()).isFalse();
        assertThat(properties("key", "secret", "", PATH).isConfigured()).isFalse();
        assertThat(properties("key", "secret", DOMAIN_NAME, "").isConfigured()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "service.test", "DESKTOP-TEAM", "ftp://service.test"})
    void 주소_형식이_아닌_DOMAINNAME은_설정되지_않은_것으로_본다(String domainName) {
        assertThat(properties("key", "secret", domainName, PATH).isConfigured()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "https://service.test, api/auth/kakao/callback",
            "https://service.test/, api/auth/kakao/callback",
            "https://service.test, /api/auth/kakao/callback",
            "https://service.test/, /api/auth/kakao/callback"
    })
    void 주소와_경로를_슬래시_하나로_잇는다(String domainName, String path) {
        assertThat(properties("key", "secret", domainName, path).getRedirectUri())
                .isEqualTo("https://service.test/api/auth/kakao/callback");
    }
}
