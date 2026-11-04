package com.devmozip.backend.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
public class ProfileIntroductionPolicy {
    private static final PolicyFactory HTML = new HtmlPolicyBuilder()
            .allowElements("p", "br", "div", "span", "h1", "h2", "h3", "h4", "h5", "h6",
                    "strong", "b", "em", "i", "u", "s", "small", "mark", "sub", "sup",
                    "blockquote", "ul", "ol", "li", "pre", "code", "hr", "a", "table",
                    "thead", "tbody", "tfoot", "tr", "th", "td", "caption", "figure", "figcaption", "img")
            .allowWithoutAttributes("div", "span", "a")
            .allowAttributes("href").matching(ProfileIntroductionPolicy::httpUrl).onElements("a")
            .allowAttributes("title").onElements("a")
            .allowAttributes("src").matching(ProfileIntroductionPolicy::httpUrl).onElements("img")
            .allowAttributes("alt", "title").onElements("img")
            .allowAttributes("colspan", "rowspan").matching(java.util.regex.Pattern.compile("[1-9][0-9]?")).onElements("th", "td")
            .allowUrlProtocols("http", "https")
            .requireRelNofollowOnLinks()
            .allowStyling()
            .toFactory();

    private static String httpUrl(String element, String attribute, String value) {
        try {
            var uri = java.net.URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null ? value : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String clean(String source, String format) {
        if (source == null || source.isBlank()) return null;
        String value = "html".equals(format) ? HTML.sanitize(source) : source;
        return value.isBlank() ? null : value;
    }
}
