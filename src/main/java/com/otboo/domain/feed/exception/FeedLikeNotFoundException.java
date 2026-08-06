package com.otboo.domain.feed.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FeedLikeNotFoundException extends FeedException {

  public FeedLikeNotFoundException(UUID feedId) {
    super(HttpStatus.BAD_REQUEST, "해당 피드에 대한 좋아요를 찾을 수 없습니다: " + feedId);
  }
}