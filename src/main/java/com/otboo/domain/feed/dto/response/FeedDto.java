package com.otboo.domain.feed.dto.response;

import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedDto(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    UserSummary author,
    WeatherSummaryDto weather,
    List<FeedOotdDto> ootds,
    String content,
    long likeCount,
    int commentCount,
    boolean likedByMe
) {
}