package com.otboo.domain.feed.exception;

import org.springframework.http.HttpStatus;

public class FeedForbiddenException extends FeedException {

  public FeedForbiddenException() {
    super(HttpStatus.BAD_REQUEST, "피드 작업 권한이 없습니다.");
  }
}