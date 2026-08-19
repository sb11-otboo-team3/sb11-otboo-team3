package com.otboo.domain.clothes.extraction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.extraction.exception.ProductInfoParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ZigzagProductExtractor implements ProductExtractor {

    private static final String PRODUCT_QUERY_KEY = "getPdpBaseInfo";
    private static final String MAIN_IMAGE_TYPE = "MAIN";

    private final ObjectMapper objectMapper;

    public ZigzagProductExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SupportedShoppingMall supportedMall() {
        return SupportedShoppingMall.ZIGZAG;
    }

    @Override
    public RawProductInfo extract(String html, String sourceUrl) {
        JsonNode product = findProductNode(html, sourceUrl);

        String name = product.path("name").asText(null);
        if (name == null || name.isBlank()) {
            throw new ProductInfoParseException(sourceUrl);
        }

        String imageUrl = extractMainImageUrl(product.path("product_image_list"));
        List<String> categoryHints = extractCategoryHints(product.path("managed_category_list"));

        // 색상 등 옵션 속성의 원본 API가 아직 확인되지 않아, 1차 범위에서는 속성 없이 반환한다.
        return new RawProductInfo(sourceUrl, name, imageUrl, categoryHints, List.of());
    }

    private JsonNode findProductNode(String html, String sourceUrl) {
        Document document = Jsoup.parse(html);
        Element script = document.selectFirst("script#__NEXT_DATA__");
        if (script == null) {
            throw new ProductInfoParseException(sourceUrl);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(script.data());
        } catch (JsonProcessingException e) {
            throw new ProductInfoParseException(sourceUrl);
        }

        for (JsonNode query : root.path("props").path("pageProps").path("dehydratedState").path("queries")) {
            JsonNode queryKey = query.path("queryKey");
            boolean isProductQuery = queryKey.isArray()
                    && queryKey.size() > 0
                    && PRODUCT_QUERY_KEY.equals(queryKey.path(0).asText());

            if (isProductQuery) {
                JsonNode product = query.path("state").path("data").path("product");
                if (!product.isMissingNode()) {
                    return product;
                }
            }
        }

        throw new ProductInfoParseException(sourceUrl);
    }

    private String extractMainImageUrl(JsonNode imageList) {
        for (JsonNode image : imageList) {
            if (MAIN_IMAGE_TYPE.equals(image.path("image_type").asText())) {
                String url = image.path("pdp_static_image_url").asText(null);
                return (url == null || url.isBlank()) ? null : url;
            }
        }
        return null;
    }

    private List<String> extractCategoryHints(JsonNode categoryList) {
        List<String> hints = new ArrayList<>();
        for (JsonNode category : categoryList) {
            String value = category.path("value").asText(null);
            String key = category.path("key").asText(null);
            if (value != null && !value.isBlank()) {
                hints.add(value);
            }
            if (key != null && !key.isBlank()) {
                hints.add(key);
            }
        }
        return hints;
    }
}
