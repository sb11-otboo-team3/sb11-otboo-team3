package com.otboo.domain.clothes.extraction.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedShoppingMallException extends ClothesExtractionException {

    public UnsupportedShoppingMallException(String host) {
        super(HttpStatus.BAD_REQUEST, "지원하지 않는 쇼핑몰입니다: " + host);
    }
}
