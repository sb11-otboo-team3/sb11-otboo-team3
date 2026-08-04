package com.otboo.domain.feed.dto.response;

import java.util.UUID;

public record FeedAuthorDto(
    UUID userId,
    String name,
    String profileImageUrl
) {
}
