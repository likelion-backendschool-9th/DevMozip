package com.devmozip.backend.dto.profile;

import java.time.OffsetDateTime;

public record LikedPostResponse(long id, String type, String category, String contentPreview,
        Author author, OffsetDateTime createdAt, OffsetDateTime likedAt, OriginalPost originalPost) {
    public record Author(long id, String nickname, String tag, String profileImage) {}
    public record OriginalPost(long id, String contentPreview, Author author) {}
}
