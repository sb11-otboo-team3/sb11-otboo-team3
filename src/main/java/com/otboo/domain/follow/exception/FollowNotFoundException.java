package com.otboo.domain.follow.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FollowNotFoundException extends FollowException {

  public FollowNotFoundException(UUID followId) {
    super(HttpStatus.NOT_FOUND, "팔로우를 찾을 수 없습니다: " + followId);
  }
}