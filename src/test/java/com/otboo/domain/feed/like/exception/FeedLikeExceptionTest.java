package com.otboo.domain.feed.like.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeedLikeExceptionTest {

  @Test
  @DisplayName("중복 피드 좋아요 예외 생성")
  void duplicateFeedLikeException_success() {
    DuplicateFeedLikeException exception = new DuplicateFeedLikeException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("피드 좋아요 없음 예외 생성")
  void feedLikeNotFoundException_success() {
    UUID feedId = UUID.randomUUID();

    FeedLikeNotFoundException exception = new FeedLikeNotFoundException(feedId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(feedId.toString());
  }
}