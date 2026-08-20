package com.otboo.domain.clothes.extraction.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public abstract class ClothesExtractionException extends OtbooException {

    protected ClothesExtractionException(HttpStatus status, String message) {
        super(status, message);
    }
}