package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.extraction.exception.UnsupportedShoppingMallException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoppingMallResolverTest {

    private final ShoppingMallResolver resolver = new ShoppingMallResolver();

    @Test
    void 무신사_URL이면_MUSINSA로_판별한다() {
        //when
        SupportedShoppingMall result = resolver.resolve(URI.create("https://www.musinsa.com/products/6841401"));

        //then
        assertThat(result).isEqualTo(SupportedShoppingMall.MUSINSA);
    }

    @Test
    void 무신사_서브도메인이어도_MUSINSA로_판별한다() {
        //when
        SupportedShoppingMall result =
                resolver.resolve(URI.create("https://goods-detail.musinsa.com/api2/goods/6841401/options"));

        //then
        assertThat(result).isEqualTo(SupportedShoppingMall.MUSINSA);
    }

    @Test
    void 지그재그_URL이면_ZIGZAG로_판별한다() {
        //when
        SupportedShoppingMall result = resolver.resolve(URI.create("https://zigzag.kr/products/12345"));

        //then
        assertThat(result).isEqualTo(SupportedShoppingMall.ZIGZAG);
    }

    @Test
    void 지원하지_않는_쇼핑몰이면_예외가_발생한다() {
        assertThatThrownBy(() -> resolver.resolve(URI.create("https://www.coupang.com/products/123")))
                .isInstanceOf(UnsupportedShoppingMallException.class);
    }

    @Test
    void 우회_시도_도메인은_지원하지_않는_쇼핑몰로_처리된다() {
        // musinsa.com을 문자열로 포함하지만 실제로는 다른 도메인인 우회 시도
        assertThatThrownBy(() -> resolver.resolve(URI.create("https://musinsa.com.attacker.net/products/1")))
                .isInstanceOf(UnsupportedShoppingMallException.class);
    }
}
