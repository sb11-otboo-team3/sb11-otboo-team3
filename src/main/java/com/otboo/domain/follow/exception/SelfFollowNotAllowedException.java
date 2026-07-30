package com.otboo.domain.follow.exception;

import org.springframework.http.HttpStatus;

public class SelfFollowNotAllowedException extends FollowException {

  public SelfFollowNotAllowedException() {
    super(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다.");
  }
}