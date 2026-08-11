package com.otboo.domain.feed.exception;

import org.springframework.http.HttpStatus;

public class InvalidFeedCommentCursorException extends FeedException {

  public InvalidFeedCommentCursorException() {
    super(HttpStatus.BAD_REQUEST, "댓글 목록 조회 커서가 올바르지 않습니다.");
  }
}