package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileLinksRequest;
import com.devmozip.backend.dto.profile.ProfileLinksResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileLinksService {
    private final MemberRepository members;
    private final ProfileLinksPolicy policy;

    public ProfileLinksResponse findPublic(String slug) {
        return policy.read(members.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)
                .orElseThrow(ProfileLinksService::notFound).getExternalLinks());
    }

    public ProfileLinksResponse findOwn(String email) {
        return policy.read(owner(email).getExternalLinks());
    }

    @Transactional
    public ProfileLinksResponse update(String email, ProfileLinksRequest request) {
        Member member = owner(email);
        var response = policy.validate(request);
        member.updateExternalLinks(policy.write(response));
        return response;
    }

    private Member owner(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        Member member = members.findByEmail(email).orElseThrow(ProfileLinksService::notFound);
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 프로필을 수정할 수 없습니다.");
        }
        return member;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");
    }
}
