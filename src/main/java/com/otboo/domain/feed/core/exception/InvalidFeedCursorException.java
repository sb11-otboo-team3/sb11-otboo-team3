package com.otboo.domain.feed.core.exception;

import org.springframework.http.HttpStatus;

public class InvalidFeedCursorException extends FeedException {

  public InvalidFeedCursorException() {
    super(HttpStatus.BAD_REQUEST, "잘못된 피드 커서입니다.");
  }
}