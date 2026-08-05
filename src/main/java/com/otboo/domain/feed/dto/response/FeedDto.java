package com.otboo.domain.feed.dto.response;

import com.otboo.domain.feed.entity.Feed;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedDto(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    FeedAuthorDto author,
    WeatherSummaryDto weather,
    List<FeedOotdDto> ootds,
    String content,
    long likeCount,
    int commentCount,
    boolean likedByMe
) {
  public static FeedDto of(
      Feed feed,
      WeatherSummaryDto weather,
      List<FeedOotdDto> ootds,
      boolean likedByMe
  ) {
    return new FeedDto(
        feed.getId(),
        feed.getCreatedAt(),
        feed.getUpdatedAt(),
        FeedAuthorDto.from(feed.getAuthor()),
        weather,
        ootds,
        feed.getContent(),
        feed.getLikeCount(),
        feed.getCommentCount(),
        likedByMe
    );
  }
}