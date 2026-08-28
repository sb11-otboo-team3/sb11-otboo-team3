package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class RequiredClothesAttributeMissingException extends OtbooException {
    public RequiredClothesAttributeMissingException(List<String> missingDefinitionNames) {
        super(HttpStatus.BAD_REQUEST, "다음 속성은 필수입니다: " + String.join(", ", missingDefinitionNames));
    }
}
