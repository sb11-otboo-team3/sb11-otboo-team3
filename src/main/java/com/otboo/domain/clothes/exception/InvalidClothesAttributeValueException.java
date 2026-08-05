package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvalidClothesAttributeValueException extends OtbooException {
    public InvalidClothesAttributeValueException(
            UUID definitionId, String value) {
        super(HttpStatus.BAD_REQUEST, "허용되지 않는 속성값입니다: " + definitionId + "=" + value);
    }
}
