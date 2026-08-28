package com.otboo.domain.follow.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FollowUserNotFoundException extends FollowException {

  // swagger 명세서상 404가 없어서 400으로 통일
  public FollowUserNotFoundException(UUID userId) {
    super(HttpStatus.BAD_REQUEST, "팔로우 사용자를 찾을 수 없습니다: " + userId);
  }
}