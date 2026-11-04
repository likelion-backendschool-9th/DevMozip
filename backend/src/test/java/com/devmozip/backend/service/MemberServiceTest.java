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
                stacks, List.of("AI"), available);
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
        return new SignupRequest("new@devmozip.demo", PASSWORD, PASSWORD, "새회원", job, "3년 차", stacks, interests, true);
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
