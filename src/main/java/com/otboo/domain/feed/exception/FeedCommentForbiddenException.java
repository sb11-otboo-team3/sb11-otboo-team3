package com.otboo.domain.feed.exception;

import org.springframework.http.HttpStatus;

public class FeedCommentForbiddenException extends FeedException {

  public FeedCommentForbiddenException() {
    super(HttpStatus.BAD_REQUEST, "댓글을 등록할 수 있는 권한이 없습니다.");
  }
}