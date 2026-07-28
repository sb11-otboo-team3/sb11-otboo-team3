package com.otboo.domain.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.otboo.domain.weather.exception.KakaoApiException;
import com.otboo.domain.weather.exception.KakaoRegionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoLocationClientTest {

  private MockRestServiceServer mockServer;
  private KakaoLocationClient kakaoLocationClient;

  @BeforeEach
  void setUp() {
    RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://dapi.kakao.com");
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    kakaoLocationClient = new KakaoLocationClient(restClientBuilder.build(), "test-api-key");
  }

  @Test
  @DisplayName("위경도로 행정동 정보를 조회하면 시/도, 시/군/구, 읍/면/동을 반환한다")
  void returnsRegionForLatLng() {
    // given
    String responseBody = """
        {
          "documents": [
            {
              "region_type": "B",
              "region_1depth_name": "서울특별시",
              "region_2depth_name": "강서구",
              "region_3depth_name": "마곡동1"
            },
            {
              "region_type": "H",
              "region_1depth_name": "서울특별시",
              "region_2depth_name": "강서구",
              "region_3depth_name": "마곡동"
            }
          ]
        }
        """;

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(header("Authorization", "KakaoAK test-api-key"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when
    KakaoRegion region = kakaoLocationClient.getRegion(37.5665, 126.9780);

    // then
    assertThat(region.province()).isEqualTo("서울특별시");
    assertThat(region.city()).isEqualTo("강서구");
    assertThat(region.district()).isEqualTo("마곡동");
  }

  @Test
  @DisplayName("카카오 API 호출이 실패하면 KakaoApiException을 던진다")
  void throwsKakaoApiExceptionWhenCallFails() {
    // given
    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(header("Authorization", "KakaoAK test-api-key"))
        .andRespond(withServerError());

    // when & then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780))
        .isInstanceOf(KakaoApiException.class);
  }

  @Test
  @DisplayName("응답에 documents가 없으면 KakaoRegionNotFoundException을 던진다")
  void throwsKakaoRegionNotFoundExceptionWhenDocumentsMissing() {
    // given
    String responseBody = """
        {}
        """;

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(header("Authorization", "KakaoAK test-api-key"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when & then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780))
        .isInstanceOf(KakaoRegionNotFoundException.class);
  }

  @Test
  @DisplayName("응답에 행정동(H) 정보가 없으면 KakaoRegionNotFoundException을 던진다")
  void throwsKakaoRegionNotFoundExceptionWhenNoAdministrativeRegion() {
    // given
    String responseBody = """
        {
          "documents": [
            {
              "region_type": "B",
              "region_1depth_name": "서울특별시",
              "region_2depth_name": "강서구",
              "region_3depth_name": "마곡동1"
            }
          ]
        }
        """;

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(header("Authorization", "KakaoAK test-api-key"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    // when & then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780))
        .isInstanceOf(KakaoRegionNotFoundException.class);
  }

  @Test
  @DisplayName("apiKey가 null이면 IllegalArgumentException을 던진다")
  void throwsIllegalArgumentExceptionWhenApiKeyIsNull() {
    // given
    RestClient restClient = RestClient.builder().baseUrl("https://dapi.kakao.com").build();

    // when & then
    assertThatThrownBy(() -> new KakaoLocationClient(restClient, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("apiKey가 빈 문자열이면 IllegalArgumentException을 던진다")
  void throwsIllegalArgumentExceptionWhenApiKeyIsBlank() {
    // given
    RestClient restClient = RestClient.builder().baseUrl("https://dapi.kakao.com").build();

    // when & then
    assertThatThrownBy(() -> new KakaoLocationClient(restClient, "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}