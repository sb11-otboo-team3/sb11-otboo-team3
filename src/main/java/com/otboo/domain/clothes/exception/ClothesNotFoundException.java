package com.otboo.domain.clothes.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ClothesNotFoundException extends OtbooException {

    public ClothesNotFoundException(UUID clothesId) {
        super(HttpStatus.BAD_REQUEST, "존재하지 않는 의상입니다: " + clothesId);
    }
}
