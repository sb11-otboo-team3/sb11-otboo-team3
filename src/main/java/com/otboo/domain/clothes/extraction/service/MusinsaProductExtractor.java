package com.otboo.domain.clothes.extraction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.extraction.dto.RawProductAttribute;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.extraction.exception.ProductInfoParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MusinsaProductExtractor implements ProductExtractor {

    private static final String IMAGE_BASE_URL = "https://image.msscdn.net";
    private static final List<String> CATEGORY_FIELDS =
            List.of("categoryDepth1Name", "categoryDepth2Name", "categoryDepth3Name");

    private final ObjectMapper objectMapper;

    public MusinsaProductExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SupportedShoppingMall supportedMall() {
        return SupportedShoppingMall.MUSINSA;
    }

    @Override
    public RawProductInfo extract(String html, String sourceUrl) {
        JsonNode product = findProductNode(html, sourceUrl);

        String name = product.path("goodsNm").asText(null);
        if (name == null || name.isBlank()) {
            throw new ProductInfoParseException(sourceUrl);
        }

        String thumbnailPath = product.path("thumbnailImageUrl").asText(null);
        String imageUrl = (thumbnailPath == null || thumbnailPath.isBlank())
                ? null
                : IMAGE_BASE_URL + thumbnailPath;

        List<String> categoryHints = extractCategoryHints(product.path("category"));
        List<RawProductAttribute> attributes =
                extractAttributes(product.path("goodsMaterial").path("materials"));

        return new RawProductInfo(sourceUrl, name, imageUrl, categoryHints, attributes);
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
            boolean isProductDetailQuery = queryKey.isArray()
                    && queryKey.size() == 2
                    && "Detail".equals(queryKey.path(0).asText())
                    && queryKey.path(1).isNumber();

            if (isProductDetailQuery) {
                JsonNode data = query.path("state").path("data").path("data");
                if (!data.isMissingNode()) {
                    return data;
                }
            }
        }

        throw new ProductInfoParseException(sourceUrl);
    }

    private List<String> extractCategoryHints(JsonNode category) {
        List<String> hints = new ArrayList<>();
        for (String field : CATEGORY_FIELDS) {
            String value = category.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                hints.add(value);
            }
        }
        return hints;
    }

    private List<RawProductAttribute> extractAttributes(JsonNode materials) {
        List<RawProductAttribute> attributes = new ArrayList<>();
        for (JsonNode material : materials) {
            String attributeName = material.path("name").asText(null);
            if (attributeName == null || attributeName.isBlank()) {
                continue;
            }

            List<String> selectedValues = new ArrayList<>();
            for (JsonNode item : material.path("items")) {
                if (item.path("isSelected").asBoolean(false)) {
                    selectedValues.add(item.path("name").asText());
                }
            }

            if (!selectedValues.isEmpty()) {
                attributes.add(new RawProductAttribute(attributeName, selectedValues));
            }
        }
        return attributes;
    }
}
