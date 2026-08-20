package com.otboo.domain.clothes.extraction.exception;

import org.springframework.http.HttpStatus;

public class ProductInfoParseException extends ClothesExtractionException {

    public ProductInfoParseException(String sourceUrl) {
        super(HttpStatus.BAD_GATEWAY, "상품 정보를 해석하지 못했습니다: " + sourceUrl);
    }
}
