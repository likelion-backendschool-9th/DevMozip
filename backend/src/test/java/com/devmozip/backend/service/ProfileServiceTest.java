package com.devmozip.backend.service;

import com.devmozip.backend.entity.JobCategory;
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
    private com.devmozip.backend.repository.JobCategoryRepository jobs;

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        jobs = mock(com.devmozip.backend.repository.JobCategoryRepository.class);
        service = new ProfileService(repository, jobs);
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
                () -> assertEquals(2L, response.jobCategory().id()),
                () -> assertEquals("백엔드", response.jobCategory().name()),
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
    @ValueSource(ints = {0, 18})
    void preservesCareerMonthsIncludingZeroAndNull(Integer months) throws Exception {
        Member user = user();
        ReflectionTestUtils.setField(user, "career", months);
        when(repository.findByTagAndStatus("dev-tag", Member.MemberStatus.ACTIVE)).thenReturn(Optional.of(user));
        assertEquals(months, service.findBySlug("dev-tag").career());
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
        var jobConstructor = JobCategory.class.getDeclaredConstructor();
        jobConstructor.setAccessible(true);
        var job = jobConstructor.newInstance();
        ReflectionTestUtils.setField(job, "id", 2L);
        ReflectionTestUtils.setField(job, "name", "백엔드");
        var constructor = Member.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var user = constructor.newInstance();
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "tag", "dev-tag");
        ReflectionTestUtils.setField(user, "nickname", "개발자");
        ReflectionTestUtils.setField(user, "jobCategoryId", 2L);
        when(jobs.findById(2L)).thenReturn(Optional.of(job));
        ReflectionTestUtils.setField(user, "summary", "함께 개발해요");
        ReflectionTestUtils.setField(user, "profileImage", "/profile.png");
        ReflectionTestUtils.setField(user, "bannerImage", "/banner.png");
        ReflectionTestUtils.setField(user, "collaborationStatus", Member.CollaborationStatus.AVAILABLE);
        ReflectionTestUtils.setField(user, "trustScore", new BigDecimal("36.50"));
        return user;
    }
}
