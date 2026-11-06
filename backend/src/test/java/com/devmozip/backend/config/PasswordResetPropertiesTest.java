package com.devmozip.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 값이 없거나 DOMAINNAME이 주소가 아닐 때 메일 발송만 꺼지는지 확인한다. */
class PasswordResetPropertiesTest {

    private static final String BASE_URL = "https://service.test";

    private static PasswordResetProperties properties(String host, String username, String password, String from, String baseUrl) {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setMailHost(host);
        properties.setMailUsername(username);
        properties.setMailPassword(password);
        properties.setFromAddress(from);
        properties.setBaseUrl(baseUrl);
        return properties;
    }

    @Test
    void 값이_모두_있으면_사용할_수_있다() {
        assertThat(properties("smtp.test", "user", "secret", "from@service.test", BASE_URL).isConfigured()).isTrue();
        assertThat(properties("smtp.test", "user", "secret", "from@service.test", "http://localhost:3000").isConfigured()).isTrue();
    }

    @Test
    void 값이_하나라도_없으면_설정되지_않은_것으로_본다() {
        assertThat(new PasswordResetProperties().isConfigured()).isFalse();
        assertThat(properties("", "user", "secret", "from@service.test", BASE_URL).isConfigured()).isFalse();
        assertThat(properties("smtp.test", "", "secret", "from@service.test", BASE_URL).isConfigured()).isFalse();
        assertThat(properties("smtp.test", "user", "", "from@service.test", BASE_URL).isConfigured()).isFalse();
        assertThat(properties("smtp.test", "user", "secret", "", BASE_URL).isConfigured()).isFalse();
        assertThat(properties("smtp.test", "user", "secret", "from@service.test", "").isConfigured()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "service.test", "DESKTOP-TEAM", "ftp://service.test"})
    void 주소_형식이_아닌_DOMAINNAME은_설정되지_않은_것으로_본다(String baseUrl) {
        assertThat(properties("smtp.test", "user", "secret", "from@service.test", baseUrl).isConfigured()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://service.test", "https://service.test/", "https://service.test//"})
    void 서비스_주소_끝의_슬래시와_관계없이_링크를_만든다(String baseUrl) {
        PasswordResetProperties properties = properties("smtp.test", "user", "secret", "from@service.test", baseUrl);

        assertThat(properties.buildResetLink("abc-DEF_123")).isEqualTo("https://service.test/forgot-password#token=abc-DEF_123");
        assertThat(properties.buildKakaoGuideLink()).isEqualTo("https://service.test/login?next=/settings/privacy");
    }
}
