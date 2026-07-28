package com.otboo.domain.weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.domain.weather.exception.KakaoApiException;
import com.otboo.domain.weather.exception.KakaoRegionNotFoundException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
public class KakaoLocationClient {

  // 행정동만 파싱. 법정동은 B
  private static final String ADMINISTRATIVE_REGION_TYPE = "H";

  private final RestClient restClient;
  private final String apiKey;

  public KakaoLocationClient(RestClient restClient, String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("카카오 API 키가 설정되지 않았습니다.");
    }
    this.restClient = restClient;
    this.apiKey = apiKey;
  }

  public KakaoRegion getRegion(double latitude, double longitude) {
    log.info("Kakao 좌표->행정구역 조회 요청 시작: latitude={}, longitude={}", latitude, longitude);

    KakaoRegionResponse response;
    try {
      response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/v2/local/geo/coord2regioncode.json")
              .queryParam("x", longitude)
              .queryParam("y", latitude)
              .build())
          .header("Authorization", "KakaoAK " + apiKey)
          .retrieve()
          .body(KakaoRegionResponse.class);
    } catch (RestClientException e) {
      log.error("Kakao 좌표->행정구역 조회 실패: latitude={}, longitude={}", latitude, longitude, e);
      throw new KakaoApiException(latitude, longitude, e);
    }

    if (response == null || response.documents() == null) {
      log.error("Kakao 응답 본문이 비어있음: latitude={}, longitude={}", latitude, longitude);
      throw new KakaoRegionNotFoundException(latitude, longitude);
    }

    KakaoRegion region = response.documents().stream()
        .filter(document -> ADMINISTRATIVE_REGION_TYPE.equals(document.regionType()))
        .findFirst()
        .map(document -> new KakaoRegion(
            document.region1depthName(),
            document.region2depthName(),
            document.region3depthName()
        ))
        .orElseThrow(() -> {
          log.error("Kakao 응답에 행정동(H) 정보가 없음: latitude={}, longitude={}", latitude, longitude);
          return new KakaoRegionNotFoundException(latitude, longitude);
        });

    log.info("Kakao 좌표->행정구역 조회 완료: latitude={}, longitude={}, province={}, city={}, district={}",
        latitude, longitude, region.province(), region.city(), region.district());

    return region;
  }

  private record KakaoRegionResponse(List<KakaoDocument> documents) {
  }

  private record KakaoDocument(
      @JsonProperty("region_type") String regionType,
      @JsonProperty("region_1depth_name") String region1depthName,
      @JsonProperty("region_2depth_name") String region2depthName,
      @JsonProperty("region_3depth_name") String region3depthName
  ) {
  }
}