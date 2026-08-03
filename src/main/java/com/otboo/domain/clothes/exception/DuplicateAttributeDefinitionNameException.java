package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class DuplicateAttributeDefinitionNameException extends OtbooException {

    public DuplicateAttributeDefinitionNameException(String name) {
        super(HttpStatus.BAD_REQUEST, "이미 등록된 속성 정의 이름입니다: " + name);
    }
}
