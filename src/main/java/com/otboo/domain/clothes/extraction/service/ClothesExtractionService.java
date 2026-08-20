package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.extraction.client.ProductPageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClothesExtractionService {

    private final ExtractionUrlValidator urlValidator;
    private final ShoppingMallResolver shoppingMallResolver;
    private final ProductPageClient productPageClient;
    private final ClothesExtractionTransactionalService clothesExtractionTransactionalService;

    public ClothesResponse extract(String url, UUID ownerId) {
        URI uri = urlValidator.validate(url);
        SupportedShoppingMall mall = shoppingMallResolver.resolve(uri);
        String html = productPageClient.fetch(uri).block();

        return clothesExtractionTransactionalService.extract(mall, html, url, ownerId);
    }
}
