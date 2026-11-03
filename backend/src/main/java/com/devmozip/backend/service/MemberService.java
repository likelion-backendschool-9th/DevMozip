package com.devmozip.backend.service;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.dto.member.MemberRequest;
import com.devmozip.backend.dto.member.MemberResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 공개 프로필 조회 - GET /api/v1/users/{slug}
    public MemberResponse getProfile(String slug) {
        Member member = memberRepository.findByTag(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));

        return MemberResponse.from(member);
    }

    // 초기 개발자 프로필 설정 / 프로필 수정 - PUT /api/v1/users/me/profile
    @Transactional
    public MemberResponse updateProfile(String email, MemberRequest request) {
        Member member = findMe(email);

        // 본인 값 그대로면 중복 검사를 건너뛴다.
        if (!Objects.equals(member.getNickname(), request.getNickname())
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
        if (!Objects.equals(member.getTag(), request.getTag())
                && memberRepository.existsByTag(request.getTag())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 사용자 태그입니다.");
        }

        member.setJobCategoryId(request.getJobCategoryId());
        member.setNickname(request.getNickname());
        member.setTag(request.getTag());
        member.setProfileImage(request.getProfileImage());
        member.setBannerImage(request.getBannerImage());
        member.setSummary(request.getSummary());

        return MemberResponse.from(member);
    }

    // 외부 링크 관리 - PUT /api/v1/users/me/links
    @Transactional
    public MemberResponse updateLinks(String email, MemberRequest request) {
        Member member = findMe(email);
        member.setExternalLinks(request.getExternalLinks());

        return MemberResponse.from(member);
    }

    // 협업 가능 상태 관리 - PATCH /api/v1/users/me/availability
    @Transactional
    public MemberResponse updateAvailability(String email, MemberRequest request) {
        if (request.getCollaborationStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "협업 가능 상태를 입력해 주세요.");
        }

        Member member = findMe(email);
        member.setCollaborationStatus(request.getCollaborationStatus());

        return MemberResponse.from(member);
    }

    // 로그인한 회원 조회 (JWT의 subject로 받은 이메일 기준)
    private Member findMe(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));
    }
}
