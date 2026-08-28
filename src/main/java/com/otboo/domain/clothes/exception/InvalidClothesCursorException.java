package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidClothesCursorException extends OtbooException {

    public InvalidClothesCursorException() {
        super(HttpStatus.BAD_REQUEST, "cursor와 idAfter는 함께 제공되어야 합니다.");
    }
}
