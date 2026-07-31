package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ClothesAttributeDefinitionNotFoundException extends OtbooException {

    public ClothesAttributeDefinitionNotFoundException(UUID definitionId) {
        super(HttpStatus.BAD_REQUEST, "존재하지 않는 의상 속성 정의입니다: " + definitionId);
    }
}
