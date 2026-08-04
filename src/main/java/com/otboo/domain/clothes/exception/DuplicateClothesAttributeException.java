package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class DuplicateClothesAttributeException extends OtbooException {

    public DuplicateClothesAttributeException() {
        super(HttpStatus.BAD_REQUEST, "동일한 속성 정의를 중복 요청할 수 없습니다.");
    }
}
