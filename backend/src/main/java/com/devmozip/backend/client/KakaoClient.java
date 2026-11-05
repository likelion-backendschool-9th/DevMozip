package com.devmozip.backend.client;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.devmozip.backend.config.KakaoProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 인증·사용자 API 호출을 맡는다.
 * 카카오 오류 본문에는 인가 코드가 들어 있을 수 있으므로 로그에는 상태와 오류 코드만 남긴다.
 */
@Component
public class KakaoClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoClient.class);

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

    private static final String REJECTED_MESSAGE = "카카오 인증을 완료하지 못했습니다. 다시 시도해 주세요.";
    private static final String UNAVAILABLE_MESSAGE = "카카오 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private static final Pattern ERROR_CODE = Pattern.compile("\"(?:error_code|code)\"\\s*:\\s*\"?(KOE\\d+|-?\\d+)\"?");

    private final KakaoProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoClient(KakaoProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()));
    }

    KakaoClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    /** 브라우저를 보낼 카카오 인가 주소를 만든다. */
    public String authorizeUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(properties.getRestApiKey(), properties.buildRedirectUri(), state)
                .toUriString();
    }

    /** 인가 코드를 카카오 액세스 토큰으로 바꾼다. 토큰은 저장하지 않고 요청을 처리하는 동안만 쓴다. */
    public String requestAccessToken(String code) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getRestApiKey());
        form.add("redirect_uri", properties.buildRedirectUri());
        form.add("code", code);
        form.add("client_secret", properties.getClientSecret());

        TokenResponse response = call("토큰 요청", () -> restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw unexpectedResponse("토큰 요청");
        }
        return response.accessToken();
    }

    /** 카카오 회원번호(응답의 id)를 조회한다. */
    public String fetchKakaoId(String accessToken) {
        requireConfigured();
        UserResponse response = call("회원번호 조회", () -> restClient.get()
                .uri(USER_URL)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(UserResponse.class));

        if (response == null || response.id() == null) {
            throw unexpectedResponse("회원번호 조회");
        }
        return String.valueOf(response.id());
    }

    /** 이 앱과 카카오 계정의 연결을 끊는다. */
    public void unlink(String accessToken) {
        requireConfigured();
        call("연결 끊기", () -> restClient.post()
                .uri(UNLINK_URL)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity());
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            log.warn("카카오 설정이 없거나 DOMAINNAME이 주소 형식이 아닙니다.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인을 사용할 수 없습니다.");
        }
    }

    private <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.warn("카카오 {} 실패: status={}, code={}", action, e.getStatusCode().value(), errorCode(e));
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, REJECTED_MESSAGE);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE_MESSAGE);
        } catch (RestClientException e) {
            log.warn("카카오 {} 실패: {}", action, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE_MESSAGE);
        }
    }

    private ResponseStatusException unexpectedResponse(String action) {
        log.warn("카카오 {} 응답에 필요한 값이 없습니다.", action);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE_MESSAGE);
    }

    private String errorCode(RestClientResponseException e) {
        Matcher matcher = ERROR_CODE.matcher(e.getResponseBodyAsString());
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserResponse(Long id) {}
}
