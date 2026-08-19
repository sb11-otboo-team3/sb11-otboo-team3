package com.otboo.domain.clothes.extraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.extraction.exception.ProductInfoParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusinsaProductExtractorTest {

    private final MusinsaProductExtractor extractor = new MusinsaProductExtractor(new ObjectMapper());

    private static final String SOURCE_URL = "https://www.musinsa.com/products/6841401";

    @Test
    void NEXT_DATA에서_상품명_이미지_카테고리_속성을_추출한다() {
        //given
        String html = wrapAsHtml("""
                {
                  "props": {
                    "pageProps": {
                      "dehydratedState": {
                        "queries": [
                          {"queryKey": ["Detail", "LoginStatus"], "state": {"data": {}}},
                          {"queryKey": ["Detail", "OtherColorGoods", 6841401], "state": {"data": {}}},
                          {"queryKey": ["Detail", "Snap_Reviews"], "state": {"data": {}}},
                          {
                            "queryKey": ["Detail", 6841401],
                            "state": {
                              "data": {
                                "meta": {"result": "SUCCESS"},
                                "data": {
                                  "goodsNm": "[2PACK] 피그먼트 머슬핏 반팔 티셔츠_11color",
                                  "thumbnailImageUrl": "/images/goods_img/20260714/6841401/6841401_500.jpg",
                                  "category": {
                                    "categoryDepth1Name": "스포츠/레저",
                                    "categoryDepth2Name": "상의",
                                    "categoryDepth3Name": "반소매 티셔츠"
                                  },
                                  "goodsMaterial": {
                                    "materials": [
                                      {
                                        "name": "핏",
                                        "items": [
                                          {"name": "슬림", "isSelected": true},
                                          {"name": "루즈", "isSelected": false}
                                        ]
                                      },
                                      {
                                        "name": "촉감",
                                        "items": [
                                          {"name": "보통", "isSelected": true}
                                        ]
                                      }
                                    ]
                                  }
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
        assertThat(result.name()).isEqualTo("[2PACK] 피그먼트 머슬핏 반팔 티셔츠_11color");
        assertThat(result.imageUrl())
                .isEqualTo("https://image.msscdn.net/images/goods_img/20260714/6841401/6841401_500.jpg");
        assertThat(result.categoryHints()).containsExactly("스포츠/레저", "상의", "반소매 티셔츠");
        assertThat(result.attributes()).hasSize(2);
        assertThat(result.attributes().get(0).name()).isEqualTo("핏");
        assertThat(result.attributes().get(0).values()).containsExactly("슬림");
        assertThat(result.attributes().get(1).name()).isEqualTo("촉감");
        assertThat(result.attributes().get(1).values()).containsExactly("보통");
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
    void Detail_상품_쿼리가_없으면_예외가_발생한다() {
        //given - LoginStatus 등 다른 Detail 쿼리만 있고 실제 상품 쿼리(["Detail", 숫자])는 없는 경우
        String html = wrapAsHtml("""
                {
                  "props": {
                    "pageProps": {
                      "dehydratedState": {
                        "queries": [
                          {"queryKey": ["Detail", "LoginStatus"], "state": {"data": {}}},
                          {"queryKey": ["Detail", "Snap_Reviews"], "state": {"data": {}}}
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
    void 상품명이_비어있으면_예외가_발생한다() {
        //given
        String html = wrapAsHtml("""
                {
                  "props": {
                    "pageProps": {
                      "dehydratedState": {
                        "queries": [
                          {
                            "queryKey": ["Detail", 6841401],
                            "state": {
                              "data": {
                                "data": {
                                  "thumbnailImageUrl": "/images/a.jpg"
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

        //when & then
        assertThatThrownBy(() -> extractor.extract(html, SOURCE_URL))
                .isInstanceOf(ProductInfoParseException.class);
    }

    @Test
    void supportedMall은_MUSINSA를_반환한다() {
        assertThat(extractor.supportedMall()).isEqualTo(SupportedShoppingMall.MUSINSA);
    }

    private String wrapAsHtml(String nextDataJson) {
        return "<html><head></head><body><script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + nextDataJson
                + "</script></body></html>";
    }
}
