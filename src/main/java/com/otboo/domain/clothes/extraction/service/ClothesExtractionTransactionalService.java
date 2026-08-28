package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.extraction.mapper.ClothesExtractionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClothesExtractionTransactionalService {

    private final List<ProductExtractor> productExtractors;
    private final ClothesExtractionMapper clothesExtractionMapper;

    @Transactional(readOnly = true)
    public ClothesResponse extract(SupportedShoppingMall mall, String html, String sourceUrl, UUID ownerId) {
        ProductExtractor extractor = findExtractor(mall);
        RawProductInfo rawProductInfo = extractor.extract(html, sourceUrl);
        return clothesExtractionMapper.toClothesResponse(rawProductInfo, ownerId);
    }

    private ProductExtractor findExtractor(SupportedShoppingMall mall) {
        return productExtractors.stream()
                .filter(extractor -> extractor.supportedMall() == mall)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("등록된 추출기가 없습니다: " + mall));
    }
}
