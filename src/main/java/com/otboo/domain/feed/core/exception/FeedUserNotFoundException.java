package com.otboo.domain.feed.core.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FeedUserNotFoundException extends FeedException {

  public FeedUserNotFoundException(UUID userId) {
    super(HttpStatus.BAD_REQUEST, "피드 작성자를 찾을 수 없습니다: " + userId);
  }
}