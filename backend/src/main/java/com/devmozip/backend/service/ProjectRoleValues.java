package com.devmozip.backend.service;

import java.text.Normalizer;
import java.util.Locale;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 기본 직군 코드는 보존하고 직접 입력한 직군은 같은 JSON 문자열로 저장한다. */
public final class ProjectRoleValues {
	private ProjectRoleValues() {}
	public static String normalize(String value) {
		if (value == null || value.chars().anyMatch(Character::isISOControl)) throw invalid();
		String text = Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll(" +", " ");
		if (text.isBlank() || text.length() > 30) throw invalid();
		if (text.equals("디자이너")) return ProjectJobRole.DESIGNER.name();
		for (var role : ProjectJobRole.values())
			if (role.name().equalsIgnoreCase(text) || role.getLabel().equalsIgnoreCase(text)) return role.name();
		return text;
	}
	public static String key(String value) { return normalize(value).toLowerCase(Locale.ROOT); }
	private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "모집 직군은 제어 문자 없이 1~30자로 입력해 주세요."); }
}
