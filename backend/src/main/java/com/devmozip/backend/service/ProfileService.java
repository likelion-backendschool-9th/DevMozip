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

    public ProfileDetailResponse findOwn(String email) {
        var member = memberRepository.findByEmail(email).orElseThrow(ProfileService::notFound);
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 프로필을 조회할 수 없습니다.");
        }
        var job = member.getJobCategoryId() == null ? null
                : jobCategoryRepository.findById(member.getJobCategoryId()).orElse(null);
        return ProfileDetailResponse.from(member, job);
    }

    public java.util.List<ProfileDetailResponse.JobCategoryResponse> jobCategories(String email) {
        findOwn(email);
        return jobCategoryRepository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                .map(job -> new ProfileDetailResponse.JobCategoryResponse(job.getId(), job.getName())).toList();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");
    }
}
