package com.devmozip.backend.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.dto.auth.SignupOptionsResponse;
import com.devmozip.backend.dto.auth.SignupRequest;
import com.devmozip.backend.dto.auth.SignupResponse;
import com.devmozip.backend.dto.member.AvailabilityRequest;
import com.devmozip.backend.dto.member.AvailabilityResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.CollaborationStatus;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.entity.enums.Interest;
import com.devmozip.backend.entity.enums.JobCategory;
import com.devmozip.backend.entity.enums.Skill;
import com.devmozip.backend.repository.MemberRepository;

@Service
public class MemberService {

    // 가입 시 신뢰 온도 초기값
    private static final BigDecimal INITIAL_TRUST_SCORE = new BigDecimal("36.50");
    // BCrypt는 72바이트까지만 처리한다. 글자 수가 64자 이내여도 한글이 많으면 넘을 수 있다.
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String DUPLICATE_EMAIL_MESSAGE = "이미 가입된 이메일입니다.";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 회원을 저장한다. 직군·기술 스택·관심 분야는 화면에서 고른 값을 users 칼럼에 그대로 담는다. */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        checkPassword(request);
        checkOptions(request);

        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_EMAIL_MESSAGE);
        }
        // 중복 확인 버튼을 누른 뒤 다른 회원이 먼저 가입했을 수 있으므로 제출할 때도 확인한다.
        String nickname = request.nickname().trim();
        if (memberRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }

        Member member = saveMember(Member.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(nickname)
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .trustScore(INITIAL_TRUST_SCORE)
                .collaborationStatus(request.available() ? CollaborationStatus.AVAILABLE : CollaborationStatus.UNAVAILABLE)
                .career(request.career())
                .jobCategories(List.of(request.job().trim()))
                .skills(distinct(request.stacks()))
                .interests(distinct(request.interests()))
                .build());

        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
    }

    /** 회원가입 화면의 선택지. 가입 때 검증하는 enum과 같은 목록을 돌려준다. */
    public SignupOptionsResponse getSignupOptions() {
        return new SignupOptionsResponse(JobCategory.labels(), Skill.labels(), Interest.labels());
    }

    /** 아직 가입되지 않은 이메일인지 DB에서 확인한다. */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(normalizeEmail(email));
    }

    /** 아직 사용되지 않은 닉네임인지 DB에서 확인한다. */
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !memberRepository.existsByNicknameIgnoreCase(nickname.trim());
    }

    /** 로그인한 회원의 협업 가능 상태와 연락 가능 시간을 바꾼다. 보낸 값만 바꾼다. */
    @Transactional
    public AvailabilityResponse updateAvailability(String email, AvailabilityRequest request) {
        if (request.collaborationStatus() == null && request.contactHours() == null) {
            throw badRequest("변경할 협업 가능 상태나 연락 가능 시간을 입력해 주세요.");
        }
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        String contactHours = request.contactHours() != null ? request.contactHours().trim() : null;
        member.updateAvailability(request.collaborationStatus(), contactHours);
        return new AvailabilityResponse(member.getCollaborationStatus(), member.getContactHours());
    }

    private List<String> distinct(List<String> values) {
        return values.stream().map(String::trim).distinct().toList();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void checkOptions(SignupRequest request) {
        if (!JobCategory.contains(request.job().trim())) {
            throw badRequest("선택할 수 없는 개발 직군입니다.");
        }
        if (!distinct(request.stacks()).stream().allMatch(Skill::contains)) {
            throw badRequest("선택할 수 없는 기술 스택이 포함되어 있습니다.");
        }
        if (!distinct(request.interests()).stream().allMatch(Interest::contains)) {
            throw badRequest("선택할 수 없는 관심 분야가 포함되어 있습니다.");
        }
    }

    private void checkPassword(SignupRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw badRequest("비밀번호가 일치하지 않습니다.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw badRequest("비밀번호가 너무 깁니다. 한글을 줄이거나 더 짧게 입력해 주세요.");
        }
    }

    private Member saveMember(Member member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 existsByEmail을 함께 통과해도 unique 제약에 걸린다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_EMAIL_MESSAGE);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
