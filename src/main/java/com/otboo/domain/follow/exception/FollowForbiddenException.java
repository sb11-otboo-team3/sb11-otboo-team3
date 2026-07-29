package com.otboo.domain.follow.exception;

import org.springframework.http.HttpStatus;

public class FollowForbiddenException extends FollowException {

  public FollowForbiddenException() {
    super(HttpStatus.FORBIDDEN, "팔로우 권한이 없습니다.");
  }
}