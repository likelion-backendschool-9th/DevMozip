package com.devmozip.backend.utils;

/** 대화에 직접 입력된 흔한 연락처/인증정보를 외부 전송 전에 가린다. 회원 DB 정보는 전송하지 않는다. */
public final class AiInputPrivacy {
	private AiInputPrivacy() {}
	public static String redact(String text) {
		if (text == null) return "";
		return text.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[이메일 제외]")
			.replaceAll("(?<![0-9])(?:\\+82[- .]?)?0?1[016789][- .]?[0-9]{3,4}[- .]?[0-9]{4}(?![0-9])", "[연락처 제외]")
			.replaceAll("AIza[A-Za-z0-9_-]{20,}", "[키 제외]")
			.replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[토큰 제외]")
			.replaceAll("(?i)(password|passwd|api[-_ ]?key|secret|비밀번호|비번)\\s*[:=]\\s*[^\\s,;\"']+", "$1=[인증정보 제외]");
	}
}
