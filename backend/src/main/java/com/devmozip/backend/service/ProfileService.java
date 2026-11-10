package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.enums.JobCategory;
import com.devmozip.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final MemberRepository memberRepository;

    public ProfileDetailResponse findBySlug(String slug) {
        var member = memberRepository.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)
                .orElseThrow(ProfileService::notFound);
        if (member.getJobCategories() == null || member.getJobCategories().isEmpty()) throw notFound();
        return ProfileDetailResponse.from(member);
    }

    public ProfileDetailResponse findOwn(String email) {
        var member = memberRepository.findByEmail(email).orElseThrow(ProfileService::notFound);
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 프로필을 조회할 수 없습니다.");
        }
        return ProfileDetailResponse.from(member);
    }

    public java.util.List<String> jobCategories(String email) {
        findOwn(email);
        return JobCategory.labels();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");
    }
}
