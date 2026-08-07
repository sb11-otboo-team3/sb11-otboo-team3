package com.otboo.domain.feed.exception;

import org.springframework.http.HttpStatus;

public class FeedClothesNotFoundException extends FeedException {

  public FeedClothesNotFoundException() {
    super(HttpStatus.BAD_REQUEST, "요청한 의상 중 존재하지 않는 의상이 있습니다.");
  }
}