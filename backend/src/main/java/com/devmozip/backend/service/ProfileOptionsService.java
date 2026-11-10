package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileOptionsRequest;
import com.devmozip.backend.dto.profile.ProfileOptionsResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.enums.Skill;
import com.devmozip.backend.entity.enums.Interest;
import com.devmozip.backend.repository.MemberRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileOptionsService {
    private final MemberRepository members;
    private final Validator validator;

    public ProfileOptionsResponse findPublic(String slug) {
        return ProfileOptionsResponse.from(members.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)
                .orElseThrow(ProfileOptionsService::notFound));
    }

    public ProfileOptionsResponse findOwn(String email) {
        return ProfileOptionsResponse.from(owner(email));
    }

    @Transactional
    public ProfileOptionsResponse update(String email, ProfileOptionsRequest request) {
        Member member = owner(email);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택 정보가 필요합니다.");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        List<String> skills = normalized(request.skills());
        List<String> interests = normalized(request.interests());
        if (!skills.stream().allMatch(Skill::contains) || !interests.stream().allMatch(Interest::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "목록에 있는 기술 스택과 관심 분야를 선택해 주세요.");
        }
        member.updateProfileOptions(skills, interests);
        return ProfileOptionsResponse.from(member);
    }

    private List<String> normalized(List<String> values) {
        return values.stream().map(String::trim).distinct().toList();
    }

    private Member owner(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        Member member = members.findByEmail(email).orElseThrow(ProfileOptionsService::notFound);
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 프로필을 수정할 수 없습니다.");
        }
        return member;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.");
    }
}
