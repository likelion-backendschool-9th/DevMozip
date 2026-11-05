package com.devmozip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

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
import com.devmozip.backend.repository.MemberRepository;

/** 가입 규칙과 저장 값을 DB 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final String PASSWORD = "Devmozip123";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private MemberService memberService;

    @Captor
    private ArgumentCaptor<Member> memberCaptor;

    private static SignupRequest request(String password, String confirmPassword, List<String> stacks, boolean available) {
        return new SignupRequest("New@Devmozip.Demo ", password, confirmPassword, " 새회원 ", "백엔드 개발자", "3년 차",
                stacks, List.of("AI"), available, null);
    }

    private static SignupRequest kakaoRequest(String password, String confirmPassword) {
        return new SignupRequest("New@Devmozip.Demo ", password, confirmPassword, " 새회원 ", "백엔드 개발자", "3년 차",
                List.of("Java"), List.of("AI"), true, "signup-token");
    }

    private void assertKakaoStatus(SignupRequest request, HttpStatus status, String message) {
        assertThatThrownBy(() -> memberService.signupWithKakao("123456789", request))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(status);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }

    @Test
    void 카카오_가입은_비밀번호_없이_카카오_회원번호를_저장한다() {
        when(memberRepository.save(any(Member.class))).then(returnsFirstArg());

        Member member = memberService.signupWithKakao("123456789", kakaoRequest(null, null));

        assertThat(member.getKakaoId()).isEqualTo("123456789");
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getEmail()).isEqualTo("new@devmozip.demo");
        assertThat(member.getNickname()).isEqualTo("새회원");
        assertThat(member.getJobCategories()).containsExactly("백엔드 개발자");
        assertThat(member.getSkills()).containsExactly("Java");
        assertThat(member.getInterests()).containsExactly("AI");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getTrustScore()).isEqualByComparingTo(new BigDecimal("36.5"));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void 카카오_가입에_비밀번호를_보내면_400() {
        assertKakaoStatus(kakaoRequest(PASSWORD, PASSWORD), HttpStatus.BAD_REQUEST, "카카오 가입에는 비밀번호를 보내지 않습니다.");
        assertKakaoStatus(kakaoRequest(null, PASSWORD), HttpStatus.BAD_REQUEST, "카카오 가입에는 비밀번호를 보내지 않습니다.");

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 이미_등록된_카카오_계정으로_가입하면_409() {
        when(memberRepository.findByKakaoId("123456789")).thenReturn(Optional.of(Member.builder().build()));

        assertKakaoStatus(kakaoRequest(null, null), HttpStatus.CONFLICT, "이미 등록된 회원입니다.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 카카오_가입의_이메일이_겹치면_자동_연결하지_않고_연동을_안내한다() {
        when(memberRepository.existsByEmail("new@devmozip.demo")).thenReturn(true);

        assertKakaoStatus(kakaoRequest(null, null), HttpStatus.CONFLICT,
                "이미 가입된 이메일입니다. 기존 계정으로 로그인한 뒤 개인정보 설정에서 카카오를 연동해 주세요.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 카카오_가입의_닉네임이_겹치면_409() {
        when(memberRepository.existsByNicknameIgnoreCase("새회원")).thenReturn(true);

        assertKakaoStatus(kakaoRequest(null, null), HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 카카오_가입이_동시에_일어나_unique_제약에_걸리면_409() {
        when(memberRepository.save(any(Member.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertKakaoStatus(kakaoRequest(null, null), HttpStatus.CONFLICT, "이미 등록된 회원이거나 사용 중인 이메일입니다.");
    }

    @Test
    void 이메일_가입에_비밀번호가_없으면_400() {
        assertStatus(request(null, null, List.of("Java"), true), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 이메일_가입에_비밀번호_확인이_없으면_400() {
        assertStatus(request(PASSWORD, null, List.of("Java"), true), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 회원과_직군_기술_스택_관심_분야를_칼럼에_저장한다() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");
        when(memberRepository.save(any(Member.class))).then(returnsFirstArg());

        SignupResponse response = memberService.signup(request(PASSWORD, PASSWORD, List.of("Java", "React"), true));

        verify(memberRepository).save(memberCaptor.capture());
        Member member = memberCaptor.getValue();
        assertThat(member.getEmail()).isEqualTo("new@devmozip.demo");
        assertThat(member.getPasswordHash()).isEqualTo("hash");
        assertThat(member.getNickname()).isEqualTo("새회원");
        assertThat(member.getJobCategories()).containsExactly("백엔드 개발자");
        assertThat(member.getSkills()).containsExactly("Java", "React");
        assertThat(member.getInterests()).containsExactly("AI");
        assertThat(member.getCareer()).isEqualTo("3년 차");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getTrustScore()).isEqualByComparingTo(new BigDecimal("36.5"));
        assertThat(member.getCollaborationStatus()).isEqualTo(CollaborationStatus.AVAILABLE);

        assertThat(response.email()).isEqualTo("new@devmozip.demo");
        assertThat(response.nickname()).isEqualTo("새회원");
    }

    @Test
    void 협업_가능을_선택하지_않으면_UNAVAILABLE로_저장한다() {
        when(memberRepository.save(any(Member.class))).then(returnsFirstArg());

        memberService.signup(request(PASSWORD, PASSWORD, List.of("Java"), false));

        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getCollaborationStatus()).isEqualTo(CollaborationStatus.UNAVAILABLE);
    }

    @Test
    void 같은_기술_스택을_두_번_보내도_한_번만_저장한다() {
        when(memberRepository.save(any(Member.class))).then(returnsFirstArg());

        memberService.signup(request(PASSWORD, PASSWORD, List.of("Java", " Java "), true));

        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getSkills()).containsExactly("Java");
    }

    @Test
    void 비밀번호_확인이_다르면_400() {
        assertStatus(request(PASSWORD, "Different123", List.of("Java"), true), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 비밀번호가_72바이트를_넘으면_500이_아니라_400() {
        String password = "가".repeat(30) + "a1";

        assertStatus(request(password, password, List.of("Java"), true), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void 이미_가입된_이메일이면_409() {
        when(memberRepository.existsByEmail("new@devmozip.demo")).thenReturn(true);

        assertStatus(request(PASSWORD, PASSWORD, List.of("Java"), true), HttpStatus.CONFLICT);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 이미_사용_중인_닉네임이면_409() {
        when(memberRepository.existsByNicknameIgnoreCase("새회원")).thenReturn(true);

        assertStatus(request(PASSWORD, PASSWORD, List.of("Java"), true), HttpStatus.CONFLICT);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 이메일_중복_확인은_소문자로_DB를_조회한다() {
        when(memberRepository.existsByEmail("new@devmozip.demo")).thenReturn(true, false);

        assertThat(memberService.isEmailAvailable(" New@Devmozip.Demo ")).isFalse();
        assertThat(memberService.isEmailAvailable("new@devmozip.demo")).isTrue();
    }

    @Test
    void 닉네임_중복_확인은_공백을_빼고_DB를_조회한다() {
        when(memberRepository.existsByNicknameIgnoreCase("새회원")).thenReturn(true, false);

        assertThat(memberService.isNicknameAvailable(" 새회원 ")).isFalse();
        assertThat(memberService.isNicknameAvailable("새회원")).isTrue();
    }

    private static final String MEMBER_EMAIL = "member@devmozip.test";

    private static Member account(String passwordHash, String kakaoId) {
        return Member.builder().email(MEMBER_EMAIL).passwordHash(passwordHash).kakaoId(kakaoId).build();
    }

    private void lookupReturns(Member member) {
        when(memberRepository.findByEmail(MEMBER_EMAIL)).thenReturn(Optional.of(member));
    }

    private void lockedLookupReturns(Member member) {
        when(memberRepository.findForAccountUpdateByEmail(MEMBER_EMAIL)).thenReturn(Optional.of(member));
    }

    private void assertAccountStatus(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(status);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }

    @Test
    void 연동_상태와_비밀번호_설정_여부를_돌려준다() {
        lookupReturns(account(null, "123456789"));

        assertThat(memberService.getSocialAccounts(MEMBER_EMAIL)).isEqualTo(new SocialAccountsResponse(true, false));
    }

    @Test
    void 없는_회원의_연동_상태를_조회하면_404() {
        when(memberRepository.findByEmail(MEMBER_EMAIL)).thenReturn(Optional.empty());

        assertAccountStatus(() -> memberService.getSocialAccounts(MEMBER_EMAIL), HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");
    }

    @Test
    void 이미_연동한_회원은_연동할_수_없다() {
        lookupReturns(account("hash", "123456789"));

        assertAccountStatus(() -> memberService.checkKakaoLinkable(MEMBER_EMAIL), HttpStatus.CONFLICT, "이미 카카오 계정이 연동되어 있습니다.");
    }

    @Test
    void 카카오_회원번호를_회원에_저장한다() {
        Member member = account("hash", null);
        lockedLookupReturns(member);

        memberService.linkKakao(MEMBER_EMAIL, "123456789");

        assertThat(member.getKakaoId()).isEqualTo("123456789");
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    void 다른_회원이_쓰는_카카오_계정을_연동하면_409() {
        Member member = account("hash", null);
        lockedLookupReturns(member);
        when(memberRepository.findByKakaoId("123456789")).thenReturn(Optional.of(account("other", "123456789")));

        assertAccountStatus(() -> memberService.linkKakao(MEMBER_EMAIL, "123456789"), HttpStatus.CONFLICT, "이미 다른 회원에 연동된 카카오 계정입니다.");

        assertThat(member.getKakaoId()).isNull();
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_연동으로_unique_제약에_걸리면_409() {
        lockedLookupReturns(account("hash", null));
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertAccountStatus(() -> memberService.linkKakao(MEMBER_EMAIL, "123456789"), HttpStatus.CONFLICT, "이미 다른 회원에 연동된 카카오 계정입니다.");
    }

    @Test
    void 해제할_수_있으면_연동된_회원번호를_돌려준다() {
        lookupReturns(account("hash", "123456789"));

        assertThat(memberService.getUnlinkableKakaoId(MEMBER_EMAIL)).isEqualTo("123456789");
    }

    @Test
    void 비밀번호가_없으면_해제할_수_없다() {
        lookupReturns(account(null, "123456789"));

        assertAccountStatus(() -> memberService.getUnlinkableKakaoId(MEMBER_EMAIL), HttpStatus.CONFLICT, "비밀번호를 먼저 설정해야 카카오 연동을 해제할 수 있습니다.");
    }

    @Test
    void 연동하지_않았으면_해제할_수_없다() {
        lookupReturns(account("hash", null));

        assertAccountStatus(() -> memberService.getUnlinkableKakaoId(MEMBER_EMAIL), HttpStatus.CONFLICT, "연동된 카카오 계정이 없습니다.");
    }

    @Test
    void 해제하면_kakao_id를_비운다() {
        Member member = account("hash", "123456789");
        lockedLookupReturns(member);

        memberService.unlinkKakao(MEMBER_EMAIL);

        assertThat(member.getKakaoId()).isNull();
    }

    @Test
    void 비밀번호_없는_회원은_마지막_단계에서도_해제되지_않는다() {
        Member member = account(null, "123456789");
        lockedLookupReturns(member);

        assertAccountStatus(() -> memberService.unlinkKakao(MEMBER_EMAIL), HttpStatus.CONFLICT, "비밀번호를 먼저 설정해야 카카오 연동을 해제할 수 있습니다.");

        assertThat(member.getKakaoId()).isEqualTo("123456789");
    }

    @Test
    void 카카오_회원의_첫_비밀번호를_해시로_저장한다() {
        Member member = account(null, "123456789");
        lockedLookupReturns(member);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");

        memberService.setPassword(MEMBER_EMAIL, new PasswordSetRequest(PASSWORD, PASSWORD));

        assertThat(member.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void 이미_비밀번호가_있으면_설정할_수_없다() {
        Member member = account("old-hash", "123456789");
        lockedLookupReturns(member);

        assertAccountStatus(() -> memberService.setPassword(MEMBER_EMAIL, new PasswordSetRequest(PASSWORD, PASSWORD)),
                HttpStatus.CONFLICT, "이미 비밀번호가 설정된 계정입니다.");

        assertThat(member.getPasswordHash()).isEqualTo("old-hash");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void 카카오를_연동하지_않았으면_비밀번호를_설정할_수_없다() {
        lockedLookupReturns(account(null, null));

        assertAccountStatus(() -> memberService.setPassword(MEMBER_EMAIL, new PasswordSetRequest(PASSWORD, PASSWORD)),
                HttpStatus.CONFLICT, "비밀번호를 설정할 수 없는 계정입니다.");

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void 새_비밀번호_확인이_다르면_400() {
        assertAccountStatus(() -> memberService.setPassword(MEMBER_EMAIL, new PasswordSetRequest(PASSWORD, "Different123")),
                HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 새_비밀번호가_72바이트를_넘으면_400() {
        String password = "가".repeat(30) + "a1";

        assertAccountStatus(() -> memberService.setPassword(MEMBER_EMAIL, new PasswordSetRequest(password, password)),
                HttpStatus.BAD_REQUEST, "비밀번호가 너무 깁니다. 한글을 줄이거나 더 짧게 입력해 주세요.");

        verifyNoInteractions(memberRepository);
    }

    private Member memberWithAvailability() {
        return Member.builder()
                .email("new@devmozip.demo")
                .collaborationStatus(CollaborationStatus.AVAILABLE)
                .contactHours("평일 저녁")
                .build();
    }

    @Test
    void 협업_상태와_연락_시간을_함께_바꾼다() {
        Member member = memberWithAvailability();
        when(memberRepository.findByEmail("new@devmozip.demo")).thenReturn(Optional.of(member));

        AvailabilityResponse response = memberService.updateAvailability("new@devmozip.demo",
                new AvailabilityRequest(CollaborationStatus.BUSY, " 주말 오전 "));

        assertThat(response).isEqualTo(new AvailabilityResponse(CollaborationStatus.BUSY, "주말 오전"));
        assertThat(member.getCollaborationStatus()).isEqualTo(CollaborationStatus.BUSY);
        assertThat(member.getContactHours()).isEqualTo("주말 오전");
    }

    @Test
    void 보낸_값만_바꾸고_나머지는_그대로_둔다() {
        Member member = memberWithAvailability();
        when(memberRepository.findByEmail("new@devmozip.demo")).thenReturn(Optional.of(member));

        AvailabilityResponse response = memberService.updateAvailability("new@devmozip.demo",
                new AvailabilityRequest(CollaborationStatus.RESTING, null));

        assertThat(response).isEqualTo(new AvailabilityResponse(CollaborationStatus.RESTING, "평일 저녁"));
    }

    @Test
    void 연락_시간을_빈_문자열로_보내면_비운다() {
        when(memberRepository.findByEmail("new@devmozip.demo")).thenReturn(Optional.of(memberWithAvailability()));

        AvailabilityResponse response = memberService.updateAvailability("new@devmozip.demo",
                new AvailabilityRequest(null, "  "));

        assertThat(response).isEqualTo(new AvailabilityResponse(CollaborationStatus.AVAILABLE, ""));
    }

    @Test
    void 바꿀_값이_하나도_없으면_400() {
        assertThatThrownBy(() -> memberService.updateAvailability("new@devmozip.demo", new AvailabilityRequest(null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 없는_회원의_상태를_바꾸면_404() {
        when(memberRepository.findByEmail("gone@devmozip.demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateAvailability("gone@devmozip.demo",
                new AvailabilityRequest(CollaborationStatus.BUSY, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static SignupRequest requestWith(String job, List<String> stacks, List<String> interests) {
        return new SignupRequest("new@devmozip.demo", PASSWORD, PASSWORD, "새회원", job, "3년 차", stacks, interests, true, null);
    }

    @Test
    void 선택지로_돌려준_값은_모두_가입에_쓸_수_있다() {
        when(memberRepository.save(any(Member.class))).then(returnsFirstArg());
        var options = memberService.getSignupOptions();

        memberService.signup(requestWith(options.jobCategories().get(0), options.skills(), options.interests()));

        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getSkills()).containsExactlyElementsOf(options.skills());
        assertThat(memberCaptor.getValue().getInterests()).containsExactlyElementsOf(options.interests());
    }

    @Test
    void 목록에_없는_직군이면_400() {
        assertStatus(requestWith("없는 직군", List.of("Java"), List.of("AI")), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 목록에_없는_기술_스택이_있으면_400() {
        assertStatus(requestWith("백엔드 개발자", List.of("Java", "COBOL"), List.of("AI")), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 목록에_없는_관심_분야가_있으면_400() {
        assertStatus(requestWith("백엔드 개발자", List.of("Java"), List.of("전체 분야")), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 동시_가입으로_unique_제약에_걸리면_409() {
        when(memberRepository.save(any(Member.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertStatus(request(PASSWORD, PASSWORD, List.of("Java"), true), HttpStatus.CONFLICT);
    }

    private void assertStatus(SignupRequest request, HttpStatus status) {
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }
}
