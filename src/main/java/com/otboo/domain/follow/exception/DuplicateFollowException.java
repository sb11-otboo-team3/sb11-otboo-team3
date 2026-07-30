package com.otboo.domain.follow.exception;

import org.springframework.http.HttpStatus;

public class DuplicateFollowException extends FollowException {

  public DuplicateFollowException() {
    super(HttpStatus.BAD_REQUEST, "이미 팔로우한 사용자입니다.");
  }
}