package com.otboo.domain.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.exception.KakaoApiException;
import com.otboo.domain.weather.exception.KakaoRegionNotFoundException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class KakaoLocationClientTest {

  private MockWebServer mockWebServer;
  private WebClient webClient;
  private KakaoLocationClient kakaoLocationClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    webClient = WebClient.builder()
        .baseUrl(mockWebServer.url("/").toString())
        .build();
    kakaoLocationClient = new KakaoLocationClient(webClient, "test-api-key");
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("위경도로 행정동 정보를 조회하면 시/도, 시/군/구, 읍/면/동을 반환한다")
  void returnsRegionForLatLng() throws InterruptedException {
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
    mockWebServer.enqueue(new MockResponse()
        .setBody(responseBody)
        .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

    // when
    KakaoRegion region = kakaoLocationClient.getRegion(37.5665, 126.9780).block();

    // then
    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665");
    assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("KakaoAK test-api-key");

    assertThat(region.province()).isEqualTo("서울특별시");
    assertThat(region.city()).isEqualTo("강서구");
    assertThat(region.district()).isEqualTo("마곡동");
  }

  @Test
  @DisplayName("카카오 API 호출이 실패하면 KakaoApiException을 던진다")
  void throwsKakaoApiExceptionWhenCallFails() {
    // given
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    // when & then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780).block())
        .isInstanceOf(KakaoApiException.class);
  }

  @Test
  @DisplayName("응답에 documents가 없으면 KakaoRegionNotFoundException을 던진다")
  void throwsKakaoRegionNotFoundExceptionWhenDocumentsMissing() {
    // given
    String responseBody = """
        {}
        """;
    mockWebServer.enqueue(new MockResponse()
        .setBody(responseBody)
        .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

    // when & then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780).block())
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
    mockWebServer.enqueue(new MockResponse()
        .setBody(responseBody)
        .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

    // when & then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780).block())
        .isInstanceOf(KakaoRegionNotFoundException.class);
  }

  @Test
  @DisplayName("apiKey가 null이면 IllegalArgumentException을 던진다")
  void throwsIllegalArgumentExceptionWhenApiKeyIsNull() {
    // when & then
    assertThatThrownBy(() -> new KakaoLocationClient(webClient, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("apiKey가 빈 문자열이면 IllegalArgumentException을 던진다")
  void throwsIllegalArgumentExceptionWhenApiKeyIsBlank() {
    // when & then
    assertThatThrownBy(() -> new KakaoLocationClient(webClient, "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}