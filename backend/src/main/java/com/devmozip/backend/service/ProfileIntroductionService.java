package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileIntroductionRequest;
import com.devmozip.backend.dto.profile.ProfileIntroductionResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileIntroductionService {
    private final MemberRepository members;
    private final ProfileIntroductionPolicy policy;
    private final Validator validator;

    public ProfileIntroductionResponse findPublic(String slug) {
        return response(members.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)
                .orElseThrow(ProfileIntroductionService::notFound));
    }

    public ProfileIntroductionResponse findOwn(String email) {
        return response(owner(email));
    }

    @Transactional
    public ProfileIntroductionResponse update(String email, ProfileIntroductionRequest request) {
        Member member = owner(email);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기소개 입력이 필요합니다.");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        String cleaned = policy.clean(request.introduction(), request.format());
        if (cleaned != null && cleaned.length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정리된 HTML을 포함해 20,000자 이내로 입력해 주세요.");
        }
        member.updateIntroduction(cleaned, cleaned == null ? "text" : request.format());
        return response(member);
    }

    private Member owner(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        Member member = members.findByEmail(email).orElseThrow(ProfileIntroductionService::notFound);
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 자기소개를 수정할 수 없습니다.");
        }
        return member;
    }

    private ProfileIntroductionResponse response(Member member) {
        String format = "html".equals(member.getIntroductionFormat()) ? "html" : "text";
        String value = policy.clean(member.getIntroduction(), format);
        return new ProfileIntroductionResponse(value, value == null ? "text" : format);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");
    }
}
