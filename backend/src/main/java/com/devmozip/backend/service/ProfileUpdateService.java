package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.dto.profile.ProfileUpdateRequest;
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
public class ProfileUpdateService {
    private final MemberRepository memberRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final ProfileUpdateValidator validator;

    @Transactional
    public ProfileDetailResponse update(String authenticatedEmail, ProfileUpdateRequest request) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        // 이메일은 요청 본문이 아니라 인증을 마친 Authentication.getName()에서만 전달한다.
        var member = memberRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 프로필을 수정할 수 없습니다.");
        }
        var input = validator.validate(request);
        var job = jobCategoryRepository.findById(input.jobCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 직군입니다."));
        if (memberRepository.existsByNicknameIgnoreCaseAndIdNot(input.nickname(), member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
        if (memberRepository.existsByTagIgnoreCaseAndIdNot(input.tag(), member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 사용자 태그입니다.");
        }
        // 검증 이후 허용한 필드만 변경한다. 비밀번호·권한·사진·신뢰 온도는 그대로 유지한다.
        member.setNickname(input.nickname());
        member.setTag(input.tag());
        member.setJobCategoryId(input.jobCategoryId());
        member.setSummary(input.summary());
        member.setCareer(input.career());
        // 새 필드가 없는 기존 클라이언트는 저장된 연락 시간을 지우지 않는다.
        // 명시적인 빈 문자열은 연락 시간 삭제를 의미한다.
        if (input.contactHours() != null) {
            member.setContactHours(input.contactHours().isEmpty() ? null : input.contactHours());
        }
        member.setCollaborationStatus(input.collaborationStatus() == null ? null
                : Member.CollaborationStatus.valueOf(input.collaborationStatus()));
        return ProfileDetailResponse.from(member, job);
    }
}
