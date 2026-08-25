package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class ThumbnailGenerationException extends OtbooException {
  public ThumbnailGenerationException(Throwable cause) {
    super(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "썸네일 생성에 실패했습니다."
    );
    initCause(cause);
  }
}