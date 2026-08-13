package com.otboo.domain.feed.comment.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeedCommentExceptionTest {

  @Test
  @DisplayName("피드 댓글 권한 없음 예외 생성")
  void feedCommentForbiddenException_success() {
    FeedCommentForbiddenException exception = new FeedCommentForbiddenException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("피드 댓글 커서 오류 예외 생성")
  void invalidFeedCommentCursorException_success() {
    InvalidFeedCommentCursorException exception = new InvalidFeedCommentCursorException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("피드 댓글 요청 오류 예외 생성")
  void invalidFeedCommentRequestException_success() {
    InvalidFeedCommentRequestException exception = new InvalidFeedCommentRequestException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }
}