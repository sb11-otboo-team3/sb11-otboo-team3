package com.otboo.domain.clothes.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.extraction.mapper.ClothesExtractionMapper;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClothesExtractionTransactionalServiceTest {

    @Mock
    private ProductExtractor musinsaExtractor;

    @Mock
    private ProductExtractor zigzagExtractor;

    @Mock
    private ClothesExtractionMapper clothesExtractionMapper;

    @Test
    void mall에_맞는_추출기를_선택해서_추출_및_매핑한다() {
        //given
        given(musinsaExtractor.supportedMall()).willReturn(SupportedShoppingMall.MUSINSA);
        // musinsaExtractor가 리스트 첫 번째라 filter().findFirst()가 바로 매칭되어 종료되므로,
        // zigzagExtractor.supportedMall()은 이 테스트에서 호출되지 않는다 (스텁하면 UnnecessaryStubbingException).

        ClothesExtractionTransactionalService service =
                new ClothesExtractionTransactionalService(List.of(musinsaExtractor, zigzagExtractor), clothesExtractionMapper);

        String html = "<html></html>";
        String sourceUrl = "https://www.musinsa.com/products/1";
        UUID ownerId = UUID.randomUUID();

        RawProductInfo rawProductInfo =
                new RawProductInfo(sourceUrl, "반팔티", null, List.of("상의"), List.of());
        ClothesResponse expected =
                new ClothesResponse(null, ownerId, "반팔티", null, ClothesType.TOP, List.of());

        given(musinsaExtractor.extract(html, sourceUrl)).willReturn(rawProductInfo);
        given(clothesExtractionMapper.toClothesResponse(rawProductInfo, ownerId)).willReturn(expected);

        //when
        ClothesResponse result = service.extract(SupportedShoppingMall.MUSINSA, html, sourceUrl, ownerId);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void 해당_mall을_지원하는_추출기가_없으면_IllegalStateException을_던진다() {
        //given
        given(zigzagExtractor.supportedMall()).willReturn(SupportedShoppingMall.ZIGZAG);

        ClothesExtractionTransactionalService service =
                new ClothesExtractionTransactionalService(List.of(zigzagExtractor), clothesExtractionMapper);

        //when & then
        assertThatThrownBy(() -> service.extract(SupportedShoppingMall.MUSINSA, "<html></html>", "url", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
