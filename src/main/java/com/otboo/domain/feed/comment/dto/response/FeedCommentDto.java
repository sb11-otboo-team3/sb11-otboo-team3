package com.otboo.domain.feed.comment.dto.response;

import com.otboo.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record FeedCommentDto(
    UUID id,
    Instant createdAt,
    UUID feedId,
    UserSummary author,
    String content
) {

}
