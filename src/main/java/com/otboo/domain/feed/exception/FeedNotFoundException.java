package com.otboo.domain.feed.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FeedNotFoundException extends FeedException {

  public FeedNotFoundException(UUID feedId) {
    super(HttpStatus.BAD_REQUEST, "피드를 찾을 수 없습니다: " + feedId);
  }
}