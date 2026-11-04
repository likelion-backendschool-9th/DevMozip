package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileUpdateRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProfileUpdateValidatorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ProfileUpdateValidator validator = new ProfileUpdateValidator(FACTORY.getValidator());

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void normalizesTextAndRetainsExplicitEmptySummary() {
        var input = new ProfileUpdateRequest("  개발자  ", "  Dev_25  ", 1L, "  ", 18, "AVAILABLE", null);
        var valid = validator.validate(input);
        assertEquals("개발자", valid.nickname());
        assertEquals("Dev_25", valid.tag());
        assertEquals("", valid.summary());
        assertEquals(18, valid.career());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 18, 120})
    void acceptsCareerMonthsWithoutConflatingZeroAndNull(Integer career) {
        var request = request("개발자", "developer", 1L, null, career, null);
        assertEquals(career, validator.validate(request).career());
        assertNull(request.summary());
        assertNull(request.collaborationStatus());
    }

    @Test
    void rejectsNegativeCareer() {
        assertInvalid("career", request("개발자", "developer", 1L, null, -1, null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "가", "invalid<script>", "abcdefghijklmnopqrstu"})
    void rejectsInvalidNickname(String nickname) {
        assertInvalid("nickname", request(nickname, "developer", 1L, null, 0, null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "a", "@developer", "../me", "한글태그", "me", "ME", "Me", "mE", "abcdefghijklmnopqrstuvwxyz12345"})
    void rejectsInvalidAndReservedTag(String tag) {
        assertInvalid("tag", request("개발자", tag, 1L, null, 0, null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void requiresPositiveJobCategoryId(Long jobId) {
        assertInvalid("jobCategoryId", request("개발자", "developer", jobId, null, 0, null));
    }

    @Test
    void enforcesSummaryLimit() {
        validator.validate(request("개발자", "developer", 1L, "가".repeat(150), 0, null));
        assertInvalid("summary", request("개발자", "developer", 1L, "가".repeat(151), 0, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "RESTING", "BUSY", "OPEN_TO_OFFERS", "UNAVAILABLE"})
    void acceptsStatusesFromCurrentMemberEntity(String status) {
        assertEquals(status, validator.validate(request("개발자", "developer", 1L, null, 0, status)).collaborationStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "available"})
    void rejectsUnknownCollaborationStatus(String status) {
        assertInvalid("collaborationStatus", request("개발자", "developer", 1L, null, 0, status));
    }

    @Test
    void reportsAllInvalidFieldsWithoutMutatingData() {
        var exception = assertThrows(ConstraintViolationException.class,
                () -> validator.validate(request("", "me", 0L, null, -1, "INVALID")));
        var fields = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
        assertEquals(Set.of("nickname", "tag", "jobCategoryId", "career", "collaborationStatus"), fields);
    }

    private void assertInvalid(String field, ProfileUpdateRequest request) {
        var exception = assertThrows(ConstraintViolationException.class, () -> validator.validate(request));
        assertTrue(exception.getConstraintViolations().stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)));
    }

    private ProfileUpdateRequest request(String nickname, String tag, Long jobId, String summary, Integer career, String status) {
        return new ProfileUpdateRequest(nickname, tag, jobId, summary, career, status, null);
    }
}
