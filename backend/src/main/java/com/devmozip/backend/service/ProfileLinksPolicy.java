package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileLinksRequest;
import com.devmozip.backend.dto.profile.ProfileLinksResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProfileLinksPolicy {
    private final Validator validator;
    private final ObjectMapper mapper;

    public ProfileLinksResponse validate(ProfileLinksRequest request) {
        if (request == null) throw invalid();
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        return new ProfileLinksResponse(url(request.github(), true), url(request.blog(), true),
                request.externalLinks().stream().map(link -> new ProfileLinksRequest.Link(link.label().strip(), url(link.url(), false))).toList());
    }

    private String url(String source, boolean optional) {
        String value = source.strip();
        if (value.isEmpty() && optional) return "";
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() > 65535
                    || value.contains("\\") || value.chars().anyMatch(Character::isISOControl)) throw invalid();
            return value;
        } catch (IllegalArgumentException exception) { throw invalid(); }
    }

    public ProfileLinksResponse read(String stored) {
        if (stored == null || stored.isBlank()) return new ProfileLinksResponse("", "", List.of());
        try {
            Object parsed = mapper.readValue(stored, Object.class);
            if (!(parsed instanceof Map<?, ?> map) || !map.keySet().equals(Set.of("github", "blog", "externalLinks"))
                    || !(map.get("github") instanceof String github) || !(map.get("blog") instanceof String blog)
                    || !(map.get("externalLinks") instanceof List<?> links)) throw invalid();
            var rows = links.stream().map(item -> {
                if (!(item instanceof Map<?, ?> link) || !link.keySet().equals(Set.of("label", "url"))
                        || !(link.get("label") instanceof String label) || !(link.get("url") instanceof String url)) throw invalid();
                return new ProfileLinksRequest.Link(label, url);
            }).toList();
            return validate(new ProfileLinksRequest(github, blog, rows));
        } catch (RuntimeException exception) {
            // 알 수 없는 기존 데이터를 빈 값으로 위장하거나 자동으로 덮어쓰지 않는다.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 외부 링크 형식을 확인해 주세요.");
        }
    }

    public String write(ProfileLinksResponse response) { return mapper.writeValueAsString(response); }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "외부 링크는 이름 40자·URL 500자 이내의 http 또는 https 주소로 입력해 주세요.");
    }
}
