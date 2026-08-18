package com.otboo.domain.clothes.extraction.exception;

import org.springframework.http.HttpStatus;

public class InvalidExtractionUrlException extends ClothesExtractionException {

    public InvalidExtractionUrlException(String url) {
        super(HttpStatus.BAD_REQUEST, "올바른 URL 형식이 아닙니다: " + url);
    }
}