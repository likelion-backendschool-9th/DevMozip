package com.devmozip.backend.utils;

import com.devmozip.backend.entity.enums.JobCategory;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.entity.enums.RequiredCareer;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class AiCandidateMatchingUtils {
	private static final Pattern YEARS = Pattern.compile("^(\\d{1,3})\\s*년\\s*(?:차|이상)?$");
	private static final Map<String, String> SKILL_ALIASES = Map.of(
		"react.js", "react", "reactjs", "react", "nodejs", "node.js", "nextjs", "next.js",
		"springboot", "spring boot", "postgres", "postgresql");

	private AiCandidateMatchingUtils() {}

	public static String text(String value) {
		return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
			.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	public static String role(String value) {
		String normalized = text(value);
		for (JobCategory role : JobCategory.values()) {
			if (normalized.equals(text(role.name())) || normalized.equals(text(role.getLabel()))) return role.name();
		}
		for (ProjectJobRole role : ProjectJobRole.values()) {
			if (normalized.equals(text(role.name())) || normalized.equals(text(role.getLabel()))) return role.name();
		}
		return "custom:" + normalized;
	}

	public static Set<String> values(List<String> input, boolean skills) {
		var result = new TreeSet<String>();
		if (input != null) for (String value : input) {
			String normalized = text(value);
			if (!normalized.isEmpty()) result.add(skills ? SKILL_ALIASES.getOrDefault(normalized, normalized) : normalized);
		}
		return result;
	}

	public static boolean hasRole(List<String> values, String requested) {
		return values != null && values.stream().anyMatch(value -> role(value).equals(role(requested)));
	}

	public static boolean careerMatches(RequiredCareer required, String career) {
		if (required == RequiredCareer.ANY) return true;
		Integer years = years(career);
		Integer minimum = years(required.label());
		return years != null && minimum != null && years >= minimum;
	}

	public static Integer years(String value) {
		String normalized = text(value);
		if (normalized.equals("신입")) return 0;
		var matcher = YEARS.matcher(normalized);
		return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
	}
}
