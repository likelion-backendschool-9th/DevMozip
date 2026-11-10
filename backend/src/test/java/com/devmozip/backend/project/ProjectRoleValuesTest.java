package com.devmozip.backend.project;

import com.devmozip.backend.service.ProjectRoleValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ProjectRoleValuesTest {
	@Test void preservesDefaultCodesAndAcceptsCustomLabels() {
		assertEquals("FRONTEND", ProjectRoleValues.normalize("프론트엔드"));
		assertEquals("BACKEND", ProjectRoleValues.normalize(" backend "));
		assertEquals("SRE", ProjectRoleValues.normalize(" ＳＲＥ "));
		assertEquals("보안 엔지니어", ProjectRoleValues.normalize("보안  엔지니어"));
		assertEquals(ProjectRoleValues.key("SRE"), ProjectRoleValues.key("sre"));
	}
	@ParameterizedTest @ValueSource(strings={"", " ", "x\ny", "x\ty", "abcdefghijklmnopqrstuvwxyz12345"})
	void rejectsBlankControlAndOverlongValues(String value) {
		assertThrows(ResponseStatusException.class, () -> ProjectRoleValues.normalize(value));
	}
}
