package com.otboo.domain.clothes.extraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.extraction.exception.ProductInfoParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZigzagProductExtractorTest {

    private final ZigzagProductExtractor extractor = new ZigzagProductExtractor(new ObjectMapper());

    private static final String SOURCE_URL = "https://zigzag.kr/catalog/products/127356723";

    @Test
    void NEXT_DATA에서_상품명_이미지_카테고리를_추출한다() {
        //given
        String html = wrapAsHtml("""
                {
                  "props": {
                    "pageProps": {
                      "dehydratedState": {
                        "queries": [
                          {"queryKey": ["getUserName"], "state": {"data": {}}},
                          {
                            "queryKey": ["getPdpBaseInfo", "127356723"],
                            "state": {
                              "data": {
                                "product": {
                                  "name": "[✨하객룩] made. 더블스티치 워싱셔츠 - 13 color",
                                  "managed_category_list": [
                                    {"value": "패션의류", "key": "fashion_clothing", "depth": 1},
                                    {"value": "여성 패션의류", "key": "women_clothes", "depth": 2},
                                    {"value": "셔츠/남방/블라우스", "key": "top_shirt-flannel", "depth": 3}
                                  ],
                                  "product_image_list": [
                                    {"image_type": "SUB", "pdp_static_image_url": "https://cf.example.com/sub.jpg"},
                                    {"image_type": "MAIN", "pdp_static_image_url": "https://cf.example.com/main.jpg"}
                                  ]
                                }
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """);

        //when
        RawProductInfo result = extractor.extract(html, SOURCE_URL);

        //then
        assertThat(result.sourceUrl()).isEqualTo(SOURCE_URL);
        assertThat(result.name()).isEqualTo("[✨하객룩] made. 더블스티치 워싱셔츠 - 13 color");
        assertThat(result.imageUrl()).isEqualTo("https://cf.example.com/main.jpg");
        assertThat(result.categoryHints())
                .containsExactly("패션의류", "fashion_clothing", "여성 패션의류", "women_clothes",
                        "셔츠/남방/블라우스", "top_shirt-flannel");
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void NEXT_DATA_스크립트가_없으면_예외가_발생한다() {
        //given
        String html = "<html><body>상품 페이지</body></html>";

        //when & then
        assertThatThrownBy(() -> extractor.extract(html, SOURCE_URL))
                .isInstanceOf(ProductInfoParseException.class);
    }

    @Test
    void getPdpBaseInfo_쿼리가_없으면_예외가_발생한다() {
        //given
        String html = wrapAsHtml("""
                {
                  "props": {
                    "pageProps": {
                      "dehydratedState": {
                        "queries": [
                          {"queryKey": ["getUserName"], "state": {"data": {}}}
                        ]
                      }
                    }
                  }
                }
                """);

        //when & then
        assertThatThrownBy(() -> extractor.extract(html, SOURCE_URL))
                .isInstanceOf(ProductInfoParseException.class);
    }

    @Test
    void 대표_이미지가_없으면_imageUrl은_null이다() {
        //given
        String html = wrapAsHtml("""
                {
                  "props": {
                    "pageProps": {
                      "dehydratedState": {
                        "queries": [
                          {
                            "queryKey": ["getPdpBaseInfo", "127356723"],
                            "state": {
                              "data": {
                                "product": {
                                  "name": "상품명",
                                  "managed_category_list": [],
                                  "product_image_list": [
                                    {"image_type": "SUB", "pdp_static_image_url": "https://cf.example.com/sub.jpg"}
                                  ]
                                }
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """);

        //when
        RawProductInfo result = extractor.extract(html, SOURCE_URL);

        //then
        assertThat(result.imageUrl()).isNull();
    }

    @Test
    void supportedMall은_ZIGZAG를_반환한다() {
        assertThat(extractor.supportedMall()).isEqualTo(SupportedShoppingMall.ZIGZAG);
    }

    private String wrapAsHtml(String nextDataJson) {
        return "<html><head></head><body><script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + nextDataJson
                + "</script></body></html>";
    }
}
