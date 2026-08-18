package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.extraction.exception.InvalidExtractionUrlException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionUrlValidatorTest {

    private final ExtractionUrlValidator validator = new ExtractionUrlValidator();

    @Test
    void https_URL이면_정상적으로_파싱된다() {
        //when
        URI result = validator.validate("https://www.musinsa.com/products/6841401");

        //then
        assertThat(result.getHost()).isEqualTo("www.musinsa.com");
    }

    @Test
    void http_URL이면_예외가_발생한다() {
        assertThatThrownBy(() -> validator.validate("http://www.musinsa.com/products/6841401"))
                .isInstanceOf(InvalidExtractionUrlException.class);
    }

    @Test
    void URL_형식이_아니면_예외가_발생한다() {
        assertThatThrownBy(() -> validator.validate("이건 URL이 아님"))
                .isInstanceOf(InvalidExtractionUrlException.class);
    }

    @Test
    void 호스트가_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> validator.validate("https:///products/6841401"))
                .isInstanceOf(InvalidExtractionUrlException.class);
    }
}
