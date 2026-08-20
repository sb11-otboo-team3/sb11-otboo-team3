package com.otboo.domain.clothes.extraction.exception;

import org.springframework.http.HttpStatus;

public class ProductPageFetchException extends ClothesExtractionException {

    public ProductPageFetchException(String url) {
        super(HttpStatus.BAD_GATEWAY, "상품 페이지를 가져오지 못했습니다: " + url);
    }
}
