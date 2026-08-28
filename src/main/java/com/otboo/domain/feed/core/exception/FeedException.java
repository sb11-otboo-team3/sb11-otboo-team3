package com.otboo.domain.feed.core.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public abstract class FeedException extends OtbooException {

  protected FeedException(HttpStatus status, String message) {
    super(status, message);
  }
}