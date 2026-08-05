package com.otboo.domain.feed.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FeedWeatherNotFoundException extends FeedException {

  public FeedWeatherNotFoundException(UUID weatherId) {
    super(HttpStatus.BAD_REQUEST, "날씨 정보를 찾을 수 없습니다: " + weatherId);
  }
}