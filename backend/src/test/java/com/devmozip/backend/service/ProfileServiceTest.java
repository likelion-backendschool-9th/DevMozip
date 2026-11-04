package com.devmozip.backend.service;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private MemberRepository repository;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        service = new ProfileService(repository);
    }

    @Test
    void returnsPublicProfileWithJobCategory() throws Exception {
        Member user = user();
        when(repository.findByTagAndStatus("dev-tag", Member.MemberStatus.ACTIVE)).thenReturn(Optional.of(user));

        var response = service.findBySlug("dev-tag");

        assertAll(
                () -> assertEquals(1L, response.id()),
                () -> assertEquals("dev-tag", response.tag()),
                () -> assertEquals("개발자", response.nickname()),
                () -> assertEquals(java.util.List.of("백엔드 개발자"), response.jobCategories()),
                () -> assertEquals("함께 개발해요", response.summary()),
                () -> assertEquals("/profile.png", response.profileImage()),
                () -> assertEquals("/banner.png", response.bannerImage()),
                () -> assertEquals("AVAILABLE", response.collaborationStatus()),
                () -> assertEquals(new BigDecimal("36.50"), response.trustScore()));
    }

    @Test
    void allowsMissingOptionalProfileFields() throws Exception {
        Member user = user();
        for (String field : new String[]{"summary", "profileImage", "bannerImage", "collaborationStatus"}) {
            ReflectionTestUtils.setField(user, field, null);
        }
        when(repository.findByTagAndStatus("dev-tag", Member.MemberStatus.ACTIVE)).thenReturn(Optional.of(user));
        var response = service.findBySlug("dev-tag");
        assertNull(response.summary());
        assertNull(response.profileImage());
        assertNull(response.bannerImage());
        assertNull(response.collaborationStatus());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"신입 / 학습 중", "2년 차"})
    void preservesCareerTextAndNull(String career) throws Exception {
        Member user = user();
        ReflectionTestUtils.setField(user, "career", career);
        when(repository.findByTagAndStatus("dev-tag", Member.MemberStatus.ACTIVE)).thenReturn(Optional.of(user));
        assertEquals(career, service.findBySlug("dev-tag").career());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "locked-account", "suspended-account"})
    void queriesOnlyActiveAccountsAndReturns404WhenAbsent(String slug) {
        when(repository.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        var exception = assertThrows(ResponseStatusException.class, () -> service.findBySlug(slug));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository).findByTagAndStatus(slug, Member.MemberStatus.ACTIVE);
    }

    private Member user() throws Exception {
        var constructor = Member.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var user = constructor.newInstance();
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "tag", "dev-tag");
        ReflectionTestUtils.setField(user, "nickname", "개발자");
        ReflectionTestUtils.setField(user, "jobCategories", java.util.List.of("백엔드 개발자"));
        ReflectionTestUtils.setField(user, "summary", "함께 개발해요");
        ReflectionTestUtils.setField(user, "profileImage", "/profile.png");
        ReflectionTestUtils.setField(user, "bannerImage", "/banner.png");
        ReflectionTestUtils.setField(user, "collaborationStatus", Member.CollaborationStatus.AVAILABLE);
        ReflectionTestUtils.setField(user, "trustScore", new BigDecimal("36.50"));
        return user;
    }
}
