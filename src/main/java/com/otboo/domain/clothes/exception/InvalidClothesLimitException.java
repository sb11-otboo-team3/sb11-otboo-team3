package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidClothesLimitException extends OtbooException {
    public InvalidClothesLimitException(int limit) {
        super(HttpStatus.BAD_REQUEST, "limit 값이 올바르지 않습니다: " + limit);
    }
}
