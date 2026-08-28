package com.otboo.domain.clothes.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.extraction.client.ProductPageClient;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ClothesExtractionServiceTest {

    @Mock
    private ExtractionUrlValidator urlValidator;

    @Mock
    private ShoppingMallResolver shoppingMallResolver;

    @Mock
    private ProductPageClient productPageClient;

    @Mock
    private ClothesExtractionTransactionalService clothesExtractionTransactionalService;

    @InjectMocks
    private ClothesExtractionService service;

    @Test
    void URL_검증_쇼핑몰_판별_HTML_요청을_거쳐_추출_결과를_반환한다() {
        //given
        String url = "https://www.musinsa.com/products/6841401";
        UUID ownerId = UUID.randomUUID();
        URI uri = URI.create(url);
        String html = "<html></html>";

        given(urlValidator.validate(url)).willReturn(uri);
        given(shoppingMallResolver.resolve(uri)).willReturn(SupportedShoppingMall.MUSINSA);
        given(productPageClient.fetch(uri)).willReturn(Mono.just(html));

        ClothesResponse expected =
                new ClothesResponse(null, ownerId, "반팔티", null, ClothesType.TOP, List.of());
        given(clothesExtractionTransactionalService.extract(SupportedShoppingMall.MUSINSA, html, url, ownerId))
                .willReturn(expected);

        //when
        ClothesResponse result = service.extract(url, ownerId);

        //then
        assertThat(result).isEqualTo(expected);
        verify(clothesExtractionTransactionalService)
                .extract(SupportedShoppingMall.MUSINSA, html, url, ownerId);
    }
}
