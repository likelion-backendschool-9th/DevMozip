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
import com.devmozip.backend.dto.member.PasswordSetRequest;
import com.devmozip.backend.dto.member.SocialAccountsResponse;
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
    private static final String DUPLICATE_EMAIL_FOR_KAKAO_MESSAGE =
            "이미 가입된 이메일입니다. 기존 계정으로 로그인한 뒤 개인정보 설정에서 카카오를 연동해 주세요.";
    private static final String ALREADY_LINKED_MESSAGE = "이미 카카오 계정이 연동되어 있습니다.";
    private static final String KAKAO_IN_USE_MESSAGE = "이미 다른 회원에 연동된 카카오 계정입니다.";
    private static final String PASSWORD_REQUIRED_MESSAGE = "비밀번호를 먼저 설정해야 카카오 연동을 해제할 수 있습니다.";

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
        checkNicknameAvailable(request);

        Member member = saveMember(memberBuilder(request, email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build(), DUPLICATE_EMAIL_MESSAGE);

        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
    }

    /** 카카오 가입. 비밀번호 없이 저장하며 kakaoId는 검증한 가입 토큰에서 꺼낸 값만 받는다. */
    @Transactional
    public Member signupWithKakao(String kakaoId, SignupRequest request) {
        if (request.password() != null || request.confirmPassword() != null) {
            throw badRequest("카카오 가입에는 비밀번호를 보내지 않습니다.");
        }
        checkOptions(request);

        if (memberRepository.findByKakaoId(kakaoId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 회원입니다.");
        }
        // 기존 계정에 자동으로 연결하지 않는다. 본인 계정이면 로그인한 뒤 직접 연동하게 안내한다.
        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_EMAIL_FOR_KAKAO_MESSAGE);
        }
        checkNicknameAvailable(request);

        return saveMember(memberBuilder(request, email).kakaoId(kakaoId).build(), "이미 등록된 회원이거나 사용 중인 이메일입니다.");
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

    /** 카카오 연동 상태와 비밀번호 설정 여부 */
    @Transactional(readOnly = true)
    public SocialAccountsResponse getSocialAccounts(String email) {
        Member member = findMember(email);
        return new SocialAccountsResponse(member.getKakaoId() != null, member.getPasswordHash() != null);
    }

    /** 인가 코드는 한 번만 쓸 수 있으므로 카카오를 호출하기 전에 연동할 수 있는 회원인지 확인한다. */
    @Transactional(readOnly = true)
    public void checkKakaoLinkable(String email) {
        if (findMember(email).getKakaoId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_LINKED_MESSAGE);
        }
    }

    /** 회원에 카카오 회원번호를 저장한다. kakaoId는 카카오에서 직접 확인한 값만 받는다. */
    @Transactional
    public void linkKakao(String email, String kakaoId) {
        Member member = findMemberForUpdate(email);
        if (member.getKakaoId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_LINKED_MESSAGE);
        }
        if (memberRepository.findByKakaoId(kakaoId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, KAKAO_IN_USE_MESSAGE);
        }
        member.linkKakao(kakaoId);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 카카오 계정을 연동하면 앞선 확인을 함께 통과해도 unique 제약에 걸린다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, KAKAO_IN_USE_MESSAGE);
        }
    }

    /**
     * 연동을 해제할 수 있으면 연동된 카카오 회원번호를 돌려준다.
     * 비밀번호가 없으면 로그인 수단이 없어지므로 카카오를 호출하기 전에 거절한다.
     */
    @Transactional(readOnly = true)
    public String getUnlinkableKakaoId(String email) {
        Member member = findMember(email);
        if (member.getKakaoId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "연동된 카카오 계정이 없습니다.");
        }
        if (member.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PASSWORD_REQUIRED_MESSAGE);
        }
        return member.getKakaoId();
    }

    @Transactional
    public void unlinkKakao(String email) {
        Member member = findMemberForUpdate(email);
        if (member.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PASSWORD_REQUIRED_MESSAGE);
        }
        member.unlinkKakao();
    }

    /** 비밀번호 없는 카카오 회원의 첫 비밀번호를 저장한다. 이미 비밀번호가 있는 회원의 변경은 지원하지 않는다. */
    @Transactional
    public void setPassword(String email, PasswordSetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw badRequest("비밀번호가 일치하지 않습니다.");
        }
        checkPasswordBytes(request.newPassword());

        Member member = findMemberForUpdate(email);
        if (member.getPasswordHash() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 비밀번호가 설정된 계정입니다.");
        }
        if (member.getKakaoId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "비밀번호를 설정할 수 없는 계정입니다.");
        }
        member.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    private Member findMemberForUpdate(String email) {
        return memberRepository.findForAccountUpdateByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
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
        if (!distinct(request.skills()).stream().allMatch(Skill::contains)) {
            throw badRequest("선택할 수 없는 기술 스택이 포함되어 있습니다.");
        }
        if (!distinct(request.interests()).stream().allMatch(Interest::contains)) {
            throw badRequest("선택할 수 없는 관심 분야가 포함되어 있습니다.");
        }
    }

    private void checkPassword(SignupRequest request) {
        if (request.password() == null) {
            throw badRequest("영문과 숫자를 포함해 8~64자로 입력해 주세요.");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw badRequest("비밀번호가 일치하지 않습니다.");
        }
        checkPasswordBytes(request.password());
    }

    private void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw badRequest("비밀번호가 너무 깁니다. 한글을 줄이거나 더 짧게 입력해 주세요.");
        }
    }

    // 중복 확인 버튼을 누른 뒤 다른 회원이 먼저 가입했을 수 있으므로 제출할 때도 확인한다.
    private void checkNicknameAvailable(SignupRequest request) {
        if (memberRepository.existsByNicknameIgnoreCase(request.nickname().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
    }

    /** 이메일 가입과 카카오 가입이 같이 저장하는 값. 비밀번호와 카카오 회원번호만 호출한 쪽에서 채운다. */
    private Member.MemberBuilder memberBuilder(SignupRequest request, String email) {
        return Member.builder()
                .email(email)
                .nickname(request.nickname().trim())
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .trustScore(INITIAL_TRUST_SCORE)
                .collaborationStatus(request.available() ? CollaborationStatus.AVAILABLE : CollaborationStatus.UNAVAILABLE)
                .career(request.career())
                .jobCategories(List.of(request.job().trim()))
                .skills(distinct(request.skills()))
                .interests(distinct(request.interests()));
    }

    private Member saveMember(Member member, String conflictMessage) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 값으로 가입하면 앞선 확인을 함께 통과해도 unique 제약에 걸린다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
