package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidSortConditionException extends OtbooException {

    public InvalidSortConditionException(String field, String value) {
        super(HttpStatus.BAD_REQUEST, "허용되지 않는 정렬 조건입니다: " + field + "=" + value);
    }
}
