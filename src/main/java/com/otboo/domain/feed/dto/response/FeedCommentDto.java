package com.otboo.domain.feed.dto.response;

import com.otboo.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record FeedCommentDto(
    UUID id,
    Instant createAt,
    UUID feedId,
    UserSummary author,
    String content
) {

}
