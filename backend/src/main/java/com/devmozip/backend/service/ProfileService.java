package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.JobCategoryRepository;
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
    private final JobCategoryRepository jobCategoryRepository;

    public ProfileDetailResponse findBySlug(String slug) {
        var member = memberRepository.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)
                .orElseThrow(ProfileService::notFound);
        if (member.getJobCategoryId() == null) throw notFound();
        var job = jobCategoryRepository.findById(member.getJobCategoryId())
                .orElseThrow(ProfileService::notFound);
        return ProfileDetailResponse.from(member, job);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");
    }
}
