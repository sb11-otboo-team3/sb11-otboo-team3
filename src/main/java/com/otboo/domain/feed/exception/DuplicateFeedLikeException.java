package com.otboo.domain.feed.exception;

import org.springframework.http.HttpStatus;

public class DuplicateFeedLikeException extends FeedException {

  public DuplicateFeedLikeException() {
      super(HttpStatus.BAD_REQUEST, "이미 좋아요를 누른 피드입니다.");
    }
}
