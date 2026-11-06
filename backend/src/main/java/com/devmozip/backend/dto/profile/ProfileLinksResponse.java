package com.devmozip.backend.dto.profile;

import java.util.List;

public record ProfileLinksResponse(String github, String blog, List<ProfileLinksRequest.Link> externalLinks) {}
