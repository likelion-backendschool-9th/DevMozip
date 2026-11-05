package com.devmozip.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.devmozip.backend.config.KakaoProperties;

/** 카카오로 보내는 요청 형식과 오류 변환을 가짜 서버로 확인한다. 실제 카카오 호출이 아니다. */
@ExtendWith(OutputCaptureExtension.class)
class KakaoClientTest {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";
    private static final String REDIRECT_URI = "https://service.test/api/auth/kakao/callback";
    private static final String CLIENT_SECRET = "client-secret-value";
    private static final String CODE = "authorization-code-value";

    private KakaoProperties properties;
    private MockRestServiceServer server;
    private KakaoClient client;

    @BeforeEach
    void setUp() {
        properties = new KakaoProperties();
        properties.setRestApiKey("rest-api-key");
        properties.setClientSecret(CLIENT_SECRET);
        properties.setDomainName("https://service.test");
        properties.setRedirectPath("api/auth/kakao/callback");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoClient(properties, builder);
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void 인가_주소에_앱_키와_Redirect_URI와_state를_담는다() {
        var params = UriComponentsBuilder.fromUriString(client.authorizeUrl("a b&c")).build(true);

        assertThat(params.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(params.getPath()).isEqualTo("/oauth/authorize");
        assertThat(params.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(params.getQueryParams().getFirst("client_id")).isEqualTo("rest-api-key");
        assertThat(params.encode().toUriString()).doesNotContain(CLIENT_SECRET);
        assertThat(params.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("https%3A%2F%2Fservice.test%2Fapi%2Fauth%2Fkakao%2Fcallback");
        // state에 쓴 문자가 다른 파라미터로 번지지 않는다
        assertThat(params.getQueryParams().getFirst("state")).isEqualTo("a%20b%26c");
    }

    @Test
    void 인가_코드와_시크릿을_폼으로_보내_액세스_토큰을_받는다() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "rest-api-key");
        form.add("redirect_uri", REDIRECT_URI);
        form.add("code", CODE);
        form.add("client_secret", CLIENT_SECRET);
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("""
                        {"token_type":"bearer","access_token":"kakao-access","expires_in":21599,
                         "refresh_token":"kakao-refresh","refresh_token_expires_in":5183999}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.requestAccessToken(CODE)).isEqualTo("kakao-access");
        server.verify();
    }

    @Test
    void 액세스_토큰으로_카카오_회원번호를_조회한다() {
        server.expect(requestTo(USER_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("""
                        {"id":123456789,"connected_at":"2026-11-05T01:45:28Z","kakao_account":{}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchKakaoId("kakao-access")).isEqualTo("123456789");
        server.verify();
    }

    @Test
    void 응답에_id가_없으면_502() {
        server.expect(requestTo(USER_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertStatus(() -> client.fetchKakaoId("kakao-access"), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 토큰_응답에_액세스_토큰이_없으면_502() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertStatus(() -> client.requestAccessToken(CODE), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 액세스_토큰으로_연결을_끊는다() {
        server.expect(requestTo(UNLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

        client.unlink("kakao-access");

        server.verify();
    }

    @Test
    void 카카오가_거절하면_400이고_로그에는_오류_코드만_남긴다(CapturedOutput output) {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":"invalid_grant","error_description":"authorization code not found for code=%s",
                         "error_code":"KOE320"}""".formatted(CODE)));

        assertStatus(() -> client.requestAccessToken(CODE), HttpStatus.BAD_REQUEST);

        assertThat(output).contains("KOE320");
        assertThat(output).doesNotContain(CODE);
        assertThat(output).doesNotContain(CLIENT_SECRET);
    }

    @Test
    void 카카오_서버_오류는_502() {
        server.expect(requestTo(USER_URL)).andRespond(withServerError());

        assertStatus(() -> client.fetchKakaoId("kakao-access"), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 카카오에_연결하지_못하면_502() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withException(new IOException("connect timed out")));

        assertStatus(() -> client.requestAccessToken(CODE), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 설정이_없으면_카카오를_호출하지_않고_503() {
        properties.setClientSecret("");

        assertStatus(() -> client.authorizeUrl("state"), HttpStatus.SERVICE_UNAVAILABLE);
        assertStatus(() -> client.requestAccessToken(CODE), HttpStatus.SERVICE_UNAVAILABLE);
        assertStatus(() -> client.fetchKakaoId("kakao-access"), HttpStatus.SERVICE_UNAVAILABLE);
        assertStatus(() -> client.unlink("kakao-access"), HttpStatus.SERVICE_UNAVAILABLE);

        server.verify();
    }
}
