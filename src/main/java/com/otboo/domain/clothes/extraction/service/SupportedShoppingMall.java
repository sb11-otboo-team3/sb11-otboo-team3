package com.otboo.domain.clothes.extraction.service;

import java.util.Arrays;
import java.util.Optional;

public enum SupportedShoppingMall {

    MUSINSA("musinsa.com"),
    ZIGZAG("zigzag.kr");

    private final String host;

    SupportedShoppingMall(String host) {
        this.host = host;
    }

    static Optional<SupportedShoppingMall> resolve(String host) {
        return Arrays.stream(values())
                .filter(mall -> mall.matches(host))
                .findFirst();
    }

    private boolean matches(String host) {
        return host.equalsIgnoreCase(this.host) || host.toLowerCase().endsWith("." + this.host);
    }
}
