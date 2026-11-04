package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileUpdateRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProfileUpdateValidatorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final List<String> JOBS = List.of("백엔드 개발자");
    private final ProfileUpdateValidator validator = new ProfileUpdateValidator(FACTORY.getValidator());

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void normalizesTextAndRetainsExplicitEmptySummary() {
        var input = new ProfileUpdateRequest("  개발자  ", "  Dev_25  ", JOBS, "  ", "2년 차", "AVAILABLE", null);
        var valid = validator.validate(input);
        assertEquals("개발자", valid.nickname());
        assertEquals("Dev_25", valid.tag());
        assertEquals("", valid.summary());
        assertEquals("2년 차", valid.career());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"신입 / 학습 중", "2년 차", "5년 이상"})
    void acceptsCareerTextAndNull(String career) {
        var request = request("개발자", "developer", JOBS, null, career, null);
        assertEquals(career, validator.validate(request).career());
        assertNull(request.summary());
        assertNull(request.collaborationStatus());
    }

    @Test
    void rejectsTooLongCareer() {
        assertInvalid("career", request("개발자", "developer", JOBS, null, "가".repeat(31), null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "가", "invalid<script>", "abcdefghijklmnopqrstu"})
    void rejectsInvalidNickname(String nickname) {
        assertInvalid("nickname", request(nickname, "developer", JOBS, null, "신입 / 학습 중", null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "a", "@developer", "../me", "한글태그", "me", "ME", "Me", "mE", "abcdefghijklmnopqrstuvwxyz12345"})
    void rejectsInvalidAndReservedTag(String tag) {
        assertInvalid("tag", request("개발자", tag, JOBS, null, "신입 / 학습 중", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void requiresJobCategories(List<String> jobs) {
        assertInvalid("jobCategories", request("개발자", "developer", jobs, null, "신입 / 학습 중", null));
    }

    @Test
    void enforcesSummaryLimit() {
        validator.validate(request("개발자", "developer", JOBS, "가".repeat(150), "신입 / 학습 중", null));
        assertInvalid("summary", request("개발자", "developer", JOBS, "가".repeat(151), "신입 / 학습 중", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "RESTING", "BUSY", "OPEN_TO_OFFERS", "UNAVAILABLE"})
    void acceptsStatusesFromCurrentMemberEntity(String status) {
        assertEquals(status, validator.validate(request("개발자", "developer", JOBS, null, "신입 / 학습 중", status)).collaborationStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "available"})
    void rejectsUnknownCollaborationStatus(String status) {
        assertInvalid("collaborationStatus", request("개발자", "developer", JOBS, null, "신입 / 학습 중", status));
    }

    @Test
    void reportsAllInvalidFieldsWithoutMutatingData() {
        var exception = assertThrows(ConstraintViolationException.class,
                () -> validator.validate(request("", "me", List.of(), null, "가".repeat(31), "INVALID")));
        var fields = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
        assertEquals(Set.of("nickname", "tag", "jobCategories", "career", "collaborationStatus"), fields);
    }

    private void assertInvalid(String field, ProfileUpdateRequest request) {
        var exception = assertThrows(ConstraintViolationException.class, () -> validator.validate(request));
        assertTrue(exception.getConstraintViolations().stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)));
    }

    private ProfileUpdateRequest request(String nickname, String tag, List<String> jobs, String summary, String career, String status) {
        return new ProfileUpdateRequest(nickname, tag, jobs, summary, career, status, null);
    }
}
