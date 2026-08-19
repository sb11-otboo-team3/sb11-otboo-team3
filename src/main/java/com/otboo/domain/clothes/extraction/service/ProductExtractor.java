package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.extraction.dto.RawProductInfo;

public interface ProductExtractor {

    SupportedShoppingMall supportedMall();

    RawProductInfo extract(String html, String sourceUrl);
}
