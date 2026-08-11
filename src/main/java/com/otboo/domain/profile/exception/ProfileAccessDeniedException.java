package com.otboo.domain.profile.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class ProfileAccessDeniedException extends OtbooException {
  public ProfileAccessDeniedException() {
    super(HttpStatus.FORBIDDEN, "본인의 프로필만 접근할 수 있습니다.");
  }
}