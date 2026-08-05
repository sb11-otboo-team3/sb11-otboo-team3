package com.otboo.domain.feed.dto.response;

import com.otboo.domain.user.entity.User;
import java.util.UUID;

public record FeedAuthorDto(
    UUID userId,
    String name,
    String profileImageUrl
) {
  public static FeedAuthorDto from(User author) {
    if (author == null) {
      return null;
    }

    return new FeedAuthorDto(
        author.getId(),
        author.getName(),
        null
    );
  }
}
