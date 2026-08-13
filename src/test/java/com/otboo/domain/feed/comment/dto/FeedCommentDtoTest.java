package com.otboo.domain.feed.comment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.feed.comment.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDto;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDtoCursorResponse;
import com.otboo.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedCommentDtoTest {

  @Test
  @DisplayName("피드 댓글 생성 요청 DTO 생성")
  void feedCommentCreateRequest_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    FeedCommentCreateRequest request =
        new FeedCommentCreateRequest(feedId, authorId, "댓글 내용");

    assertThat(request.feedId()).isEqualTo(feedId);
    assertThat(request.authorId()).isEqualTo(authorId);
    assertThat(request.content()).isEqualTo("댓글 내용");
  }

  @Test
  @DisplayName("피드 댓글 응답 DTO 생성")
  void feedCommentDto_success() {
    UUID commentId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");
    UserSummary author = new UserSummary(authorId, "author", null);

    FeedCommentDto commentDto = new FeedCommentDto(
        commentId,
        createdAt,
        feedId,
        author,
        "댓글 내용"
    );

    assertThat(commentDto.id()).isEqualTo(commentId);
    assertThat(commentDto.createdAt()).isEqualTo(createdAt);
    assertThat(commentDto.feedId()).isEqualTo(feedId);
    assertThat(commentDto.author()).isEqualTo(author);
    assertThat(commentDto.content()).isEqualTo("댓글 내용");
  }

  @Test
  @DisplayName("피드 댓글 커서 응답 DTO 생성")
  void feedCommentDtoCursorResponse_success() {
    UUID nextIdAfter = UUID.randomUUID();

    FeedCommentDtoCursorResponse response = new FeedCommentDtoCursorResponse(
        List.of(),
        "2026-08-13T01:00:00Z",
        nextIdAfter,
        true,
        10L,
        "createdAt",
        "ASCENDING"
    );

    assertThat(response.data()).isEmpty();
    assertThat(response.nextCursor()).isEqualTo("2026-08-13T01:00:00Z");
    assertThat(response.nextIdAfter()).isEqualTo(nextIdAfter);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.totalCount()).isEqualTo(10L);
    assertThat(response.sortBy()).isEqualTo("createdAt");
    assertThat(response.sortDirection()).isEqualTo("ASCENDING");
  }
}