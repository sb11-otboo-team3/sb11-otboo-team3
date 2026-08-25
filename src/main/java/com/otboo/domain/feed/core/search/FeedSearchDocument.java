package com.otboo.domain.feed.core.search;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import java.util.UUID;

public record FeedSearchDocument(
    UUID id,
    UUID authorId,
    String content,
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    Instant createdAt,
    long likeCount
) {

  public static FeedSearchDocument from(
      Feed feed,
      WeatherSummaryDto weatherSummary
  ) {
    return new FeedSearchDocument(
        feed.getId(),
        feed.getAuthor().getId(),
        feed.getContent(),
        weatherSummary.skyStatus(),
        weatherSummary.precipitation().type(),
        feed.getCreatedAt(),
        feed.getLikeCount()
    );
  }
}