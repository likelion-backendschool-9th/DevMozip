package com.devmozip.backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileIntroductionPolicyTest {
    private final ProfileIntroductionPolicy policy = new ProfileIntroductionPolicy();
    @Test
    void preservesTextAndLineBreaksWithoutInterpretingHtml() {
        assertEquals("<script>문자 그대로</script>\n둘째 줄", policy.clean("<script>문자 그대로</script>\n둘째 줄", "text"));
        assertNull(policy.clean("  ", "text"));
    }
    @Test
    void allowsEditorFormattingAndAbsoluteHttpLinksOnly() {
        String cleaned = policy.clean("<h2>소개</h2><ul><li>경험</li></ul><table><tr><td>기록</td></tr></table>"
                + "<a href='https://example.com'>정상</a><a href='//example.com'>상대</a><img src='data:image/svg+xml,x' onerror='alert(1)' />", "html");
        assertTrue(cleaned.contains("<h2>소개</h2>")); assertTrue(cleaned.contains("<li>경험</li>"));
        assertTrue(cleaned.contains("<td>기록</td>")); assertTrue(cleaned.contains("href=\"https://example.com\""));
        assertFalse(cleaned.contains("href=\"//")); assertFalse(cleaned.contains("data:")); assertFalse(cleaned.contains("onerror"));
    }
    @Test
    void excludesExecutableMarkupAndUnsafeCss() {
        String cleaned = policy.clean("<script>alert(1)</script><svg onload='alert(1)'></svg>"
                + "<p style='color:red; background-image:url(https://example.com/x); position:fixed' onclick='alert(1)'>내용</p>"
                + "<a href='jav&#x61;script:alert(1)'>링크</a>", "html");
        for (String value : new String[]{"<script", "<svg", "onload", "onclick", "javascript:", "url(", "position:"}) assertFalse(cleaned.contains(value), cleaned);
        assertTrue(cleaned.contains("내용"));
    }
}
