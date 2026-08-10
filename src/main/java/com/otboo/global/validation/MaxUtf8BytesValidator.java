package com.otboo.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

  private int maxBytes;

  @Override
  public void initialize(MaxUtf8Bytes constraintAnnotation) {
    this.maxBytes = constraintAnnotation.value();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      // null 처리는 @NotBlank 등 다른 어노테이션에게 위임한다.
      return true;
    }
    return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
  }
}