package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.extraction.exception.UnsupportedShoppingMallException;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ShoppingMallResolver {

    public SupportedShoppingMall resolve(URI uri) {
        return SupportedShoppingMall.resolve(uri.getHost())
                .orElseThrow(() -> new UnsupportedShoppingMallException(uri.getHost()));
    }
}
