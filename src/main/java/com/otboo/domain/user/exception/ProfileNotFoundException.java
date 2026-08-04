package com.otboo.domain.user.exception;

import com.otboo.global.error.OtbooException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ProfileNotFoundException extends OtbooException {

  public ProfileNotFoundException(UUID userId) {
    super(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다: " + userId);
  }
}