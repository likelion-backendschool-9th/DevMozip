package com.devmozip.backend.ai;

import com.devmozip.backend.utils.AiInputPrivacy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiInputPrivacyTest {
	@Test void removesCommonContactAndCredentialPatternsWithoutRemovingRequirements() {
		String text = AiInputPrivacy.redact("Java와 결제 기능을 배우고 싶어요. me@example.com 010-1234-5678 password=private-value api_key=private-key");
		assertTrue(text.contains("Java와 결제 기능"));
		for (String forbidden : new String[]{"me@example", "010-1234", "private-value", "private-key"}) assertFalse(text.contains(forbidden));
	}
}
