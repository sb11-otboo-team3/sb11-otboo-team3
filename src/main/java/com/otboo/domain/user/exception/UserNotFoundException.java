package com.otboo.domain.user.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

public class UserNotFoundException extends OtbooException {

  public UserNotFoundException(UUID userId) {
    super(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다: " + userId);
  }

  public UserNotFoundException(String email) {
    super(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다: " + email);
  }
}