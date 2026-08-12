package com.otboo.domain.profile.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class ProfileConcurrentUpdateException extends OtbooException {
  public ProfileConcurrentUpdateException() {
    super(
        HttpStatus.CONFLICT,
        "다른 요청에 의해 프로필이 동시에 수정되었습니다. 다시 시도해주세요."
    );
  }
}